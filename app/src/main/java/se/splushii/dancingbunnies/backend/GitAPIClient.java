package se.splushii.dancingbunnies.backend;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.HiddenFileFilter;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import se.splushii.dancingbunnies.musiclibrary.AudioDataSource;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Playlist;
import se.splushii.dancingbunnies.musiclibrary.StupidPlaylist;
import se.splushii.dancingbunnies.musiclibrary.export.SchemaValidator;
import se.splushii.dancingbunnies.util.Util;

public class GitAPIClient extends APIClient {
    private static final String LC = Util.getLogContext(GitAPIClient.class);

    public static final String PROTOCOL_HTTPS = "https";
    public static final String PROTOCOL_SSH = "ssh";

    private static final String REMOTE = "origin";

    private Path workDir;
    private String gitURI;
    private String gitBranch;
    private String sshPrivKey;
    private String sshPassphrase;
    private String username;
    private String password;

    private TransportConfigCallback transportConfigCallback;

    public GitAPIClient(String src) {
        super(src);
    }

    @Override
    public boolean hasLibrary() {
        return false;
    }

    @Override
    public boolean hasPlaylists() {
        return true;
    }

    @Override
    public void loadSettings(Context context, Path workDir, Bundle settings) {
        this.workDir = workDir;
        gitURI = settings.getString(APIClient.SETTINGS_KEY_GIT_REPO);
        username = settings.getString(APIClient.SETTINGS_KEY_GIT_USERNAME);
        password = settings.getString(APIClient.SETTINGS_KEY_GIT_PASSWORD);
        sshPrivKey = settings.getString(APIClient.SETTINGS_KEY_GIT_SSH_KEY);
        sshPassphrase = settings.getString(APIClient.SETTINGS_KEY_GIT_SSH_KEY_PASSPHRASE);
        gitBranch = settings.getString(APIClient.SETTINGS_KEY_GIT_BRANCH);

        transportConfigCallback = transport -> {
            transport.setTimeout(30);
            if (transport instanceof SshTransport) {
                configureSSHTransport(transport);
            } else if (transport instanceof HttpTransport) {
                transport.setCredentialsProvider(new UsernamePasswordCredentialsProvider(
                        username != null ? username : "",
                        password != null ? password : ""
                ));
            } else {
                Log.e(LC, "Could not configure Git transport. Only SSH and HTTP supported.");
            }
        };
//        tagDelimiter = settings.getString(APIClient.SETTINGS_KEY_DB_TAG_DELIM);
    }

    @Override
    public CompletableFuture<Void> heartbeat() {
        return CompletableFuture.supplyAsync(() -> {
            LsRemoteCommand lsRemoteCommand = Git.lsRemoteRepository()
                    .setTransportConfigCallback(transportConfigCallback)
                    .setRemote(gitURI);
            try {
                lsRemoteCommand.call();
            } catch (InvalidRemoteException e) {
                e.printStackTrace();
                throw new Util.FutureException("Invalid remote: " + e.getMessage());
            } catch (TransportException e) {
                e.printStackTrace();
                throw new Util.FutureException("Transport error: " + e.getMessage());
            } catch (GitAPIException e) {
                e.printStackTrace();
                throw new Util.FutureException("GIT API error: " + e.getMessage());
            }
            return null;
        });
    }

    private void configureSSHTransport(Transport transport) {
        SshTransport sshTransport = (SshTransport) transport;
        sshTransport.setSshSessionFactory(new JschConfigSessionFactory() {
            @Override
            protected void configure(OpenSshConfig.Host hc, Session session) {
                // TODO: Enable if possible (use Android bundled CA:s?)
                session.setConfig("StrictHostKeyChecking", "no");
                if (password != null) {
                    session.setPassword(password);
                }
            }

            @Override
            protected JSch createDefaultJSch(FS fs) throws JSchException {
                JSch jSch = super.createDefaultJSch(fs);
                if (sshPrivKey != null) {
                    jSch.addIdentity(
                            "id",
                            sshPrivKey.getBytes(),
                            null,
                            sshPassphrase == null ? null : sshPassphrase.getBytes()
                    );
                }
                return jSch;
            }
        });
    }

    @Override
    public CompletableFuture<Optional<List<Playlist>>> getPlaylists(APIClientRequestHandler handler) {
        Git git = null;
        File workDirFile = workDir.toFile();
        if (workDirFile.exists() && workDirFile.isDirectory()) {
            Log.d(LC, "getPlaylists: Existing workdir at: " + workDir.toString());
            if (!workDirFile.canRead() || !workDirFile.canWrite() || !workDirFile.canExecute()) {
                return Util.futureResult(
                        "getPlaylists: Not enough permissions to use workdir: "
                                + workDir.toString()
                );
            }
            try {
                Log.d(LC, "getPlaylists: Trying to use existing repository at: "
                        + workDir.toString());
                git = Git.open(workDir.toFile());
            } catch (RepositoryNotFoundException e) {
                Log.d(LC, "getPlaylists: Repository not found at: " + workDir.toString());
                git = null;
            } catch (IOException e) {
                return Util.futureResult(
                        "getPlaylists: Could not reuse repository: " + workDir.toString()
                                + " : " + e.getMessage()
                );
            }
            if (git != null) {
                Log.d(LC, "getPlaylists: Using existing repository at: " + workDir.toString());
                String currentBranch;
                try {
                    currentBranch = git.getRepository().getBranch();
                } catch (IOException e) {
                    return Util.futureResult(
                            "getPlaylists: Could not get current branch: " + e.getMessage()
                    );
                }
                if (!currentBranch.equals(gitBranch)) {
                    Log.d(LC, "getPlaylists: Current branch \"" + currentBranch + "\""
                            + " does not match target branch \"" + gitBranch + "\"."
                            + " Checking out target branch.");
                    try {
                        git.checkout()
                                .setName(gitBranch)
                                .setCreateBranch(true)
                                .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                                .setStartPoint(REMOTE + "/" + gitBranch)
                                .call();
                    } catch (GitAPIException e) {
                        return Util.futureResult(
                                "getPlaylists: Could not checkout target branch: " + e.getMessage()
                        );
                    }
                    Log.d(LC, "getPlaylists: Successfully checked out target branch \"" + gitBranch + "\"");
                }
                Log.d(LC, "getPlaylists: Trying to pull latest changes");
                try {
                    PullResult pullResult = git.pull()
                            .setTransportConfigCallback(transportConfigCallback)
                            .setRemoteBranchName(gitBranch)
                            .setRebase(true)
                            .call();
                    if (!pullResult.isSuccessful()) {
                        return Util.futureResult(
                                "getPlaylists: Could not pull changes"
                                        + "\nfetch: "
                                        + pullResult.getFetchResult().getMessages()
                                        + "\nrebase success: "
                                        + pullResult.getRebaseResult().getStatus().isSuccessful()
                                        + "\nmerge success: "
                                        + pullResult.getMergeResult().getMergeStatus().isSuccessful()
                        );
                    } else {
                        Log.d(LC, "getPlaylists:"
                                + " Successfully pulled and rebased against latest changes");
                    }
                } catch (GitAPIException e) {
                    return Util.futureResult(
                            "getPlaylists: Could not pull and rebase against "
                                    + "\"" + gitBranch + "\": " + e.getMessage()
                    );
                }
            }
        }
        if (git == null) {
            Log.d(LC, "getPlaylists: Trying to clone the remote repository");
            CloneCommand cloneCommand = Git.cloneRepository()
                    .setDirectory(workDir.toFile())
                    .setTransportConfigCallback(transportConfigCallback)
                    .setBranch(gitBranch)
                    .setURI(gitURI);
            try {
                git = cloneCommand.call();
                Log.d(LC, "getPlaylists: Successfully cloned the remote repository");
            } catch (GitAPIException e) {
                return Util.futureResult(
                        "getPlaylists: Could not clone repository: " + e.getMessage()
                );
            }
        }
        Log.d(LC, "getPlaylist: Local working tree is up to date. Traversing files...");
        List<Path> playlistPaths;
        try (Stream<Path> walk = Files.walk(Paths.get(workDir.toUri()))) {
            playlistPaths = walk
                    .filter(Files::isRegularFile)
                    .filter(path -> HiddenFileFilter.VISIBLE.accept(path.toFile()))
                    .filter(path -> FilenameUtils.isExtension(path.toString(), SchemaValidator.PLAYLIST_SUFFIX))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            git.close();
            return Util.futureResult("getPlaylists: Could not traverse repository"
                    + " for playlist files: " + e.getMessage());
        }
        Log.d(LC, "getPlaylists: Valid playlist files:\n"
                + playlistPaths.stream()
                .map(Path::toString)
                .collect(Collectors.joining("\n"))
        );
        List<Playlist> playlists = playlistPaths.stream()
                .map(path -> StupidPlaylist.from(src, path))
                .collect(Collectors.toList());
        git.close();
        return CompletableFuture.completedFuture(Optional.of(playlists));
    }

    @Override
    public AudioDataSource getAudioData(EntryID entryID) {
        return null;
    }
}
