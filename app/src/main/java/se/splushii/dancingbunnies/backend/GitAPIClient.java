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
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import androidx.core.util.Consumer;
import se.splushii.dancingbunnies.musiclibrary.AudioDataSource;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.Playlist;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.musiclibrary.StupidPlaylist;
import se.splushii.dancingbunnies.musiclibrary.export.SchemaValidator;
import se.splushii.dancingbunnies.storage.transactions.TransactionPlaylistAdd;
import se.splushii.dancingbunnies.storage.transactions.TransactionPlaylistDelete;
import se.splushii.dancingbunnies.storage.transactions.TransactionPlaylistEntryAdd;
import se.splushii.dancingbunnies.storage.transactions.TransactionPlaylistEntryDelete;
import se.splushii.dancingbunnies.storage.transactions.TransactionPlaylistEntryMove;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.storage.transactions.Transaction.PLAYLIST_ADD;
import static se.splushii.dancingbunnies.storage.transactions.Transaction.PLAYLIST_DELETE;
import static se.splushii.dancingbunnies.storage.transactions.Transaction.PLAYLIST_ENTRY_ADD;
import static se.splushii.dancingbunnies.storage.transactions.Transaction.PLAYLIST_ENTRY_DELETE;
import static se.splushii.dancingbunnies.storage.transactions.Transaction.PLAYLIST_ENTRY_MOVE;

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
    private String tagDelimiter;

    private TransportConfigCallback transportConfigCallback;

    public GitAPIClient(String apiInstanceID) {
        super(MusicLibraryService.API_SRC_ID_GIT, apiInstanceID);
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
        tagDelimiter = settings.getString(APIClient.SETTINGS_KEY_DB_TAG_DELIM);
    }

    @Override
    public boolean supports(String action, String argumentSource) {
        if (action == null) {
            return false;
        }
        String argumentAPI = MusicLibraryService.getAPIFromSource(argumentSource);
        switch (argumentAPI) {
            default:
                return false;
            case MusicLibraryService.API_SRC_ID_DANCINGBUNNIES:
            case MusicLibraryService.API_SRC_ID_SUBSONIC:
            case MusicLibraryService.API_SRC_ID_GIT:
                break;
        }
        switch (action) {
            case PLAYLIST_ADD:
            case PLAYLIST_DELETE:
            case PLAYLIST_ENTRY_ADD:
            case PLAYLIST_ENTRY_DELETE:
            case PLAYLIST_ENTRY_MOVE:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean supportsDancingBunniesSmartPlaylist() {
        return true;
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

    @Override
    public AudioDataSource getAudioData(EntryID entryID) {
        return null;
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

    private String updateWorkDir() {
        Git git = null;
        File workDirFile = workDir.toFile();
        if (workDirFile.exists() && workDirFile.isDirectory()) {
            Log.d(LC, "Existing workdir at: " + workDir.toString());
            if (!workDirFile.canRead() || !workDirFile.canWrite() || !workDirFile.canExecute()) {
                return "Not enough permissions to use workdir: " + workDir.toString();
            }
            try {
                Log.d(LC, "Trying to use existing repository at: "
                        + workDir.toString());
                git = Git.open(workDir.toFile());
            } catch (RepositoryNotFoundException e) {
                Log.d(LC, "Repository not found at: " + workDir.toString());
                git = null;
            } catch (IOException e) {
                return "Could not reuse repository: " + workDir.toString()
                        + " : " + e.getMessage();
            }
            if (git != null) {
                Log.d(LC, "Using existing repository at: " + workDir.toString());
                Status gitStatus;
                try {
                    gitStatus = git.status().call();
                } catch (GitAPIException e) {
                    return "Could not get git status: " + e.getMessage();
                }
                if (!gitStatus.isClean()) {
                    Log.d(LC, "Working tree is not clean. Resetting...");
                    try {
                        git.reset()
                                .setMode(ResetCommand.ResetType.HARD)
                                .call();
                    } catch (GitAPIException e) {
                        return "Could not reset working tree: " + e.getMessage();
                    }
                }
                String currentBranch;
                try {
                    currentBranch = git.getRepository().getBranch();
                } catch (IOException e) {
                    return "Could not get current branch: " + e.getMessage();
                }
                if (!currentBranch.equals(gitBranch)) {
                    Log.d(LC, "Current branch \"" + currentBranch + "\""
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
                        return "Could not checkout target branch: " + e.getMessage();
                    }
                    Log.d(LC, "Successfully checked out target branch \"" + gitBranch + "\"");
                }
                Log.d(LC, "Trying to pull latest changes");
                try {
                    PullResult pullResult = git.pull()
                            .setTransportConfigCallback(transportConfigCallback)
                            .setRemoteBranchName(gitBranch)
                            .setRebase(true)
                            .call();
                    if (!pullResult.isSuccessful()) {
                        return "Could not pull changes"
                                + "\nfetch: "
                                + pullResult.getFetchResult().getMessages()
                                + "\nrebase success: "
                                + pullResult.getRebaseResult().getStatus().isSuccessful()
                                + "\nmerge success: "
                                + pullResult.getMergeResult().getMergeStatus().isSuccessful();
                    } else {
                        Log.d(LC, "Successfully pulled and rebased against latest changes");
                    }
                } catch (GitAPIException e) {
                    return "Could not pull and rebase against "
                            + "\"" + gitBranch + "\": " + e.getMessage();
                }
            }
        }
        if (git == null) {
            Log.d(LC, "Trying to clone the remote repository");
            CloneCommand cloneCommand = Git.cloneRepository()
                    .setDirectory(workDir.toFile())
                    .setTransportConfigCallback(transportConfigCallback)
                    .setBranch(gitBranch)
                    .setURI(gitURI);
            try {
                git = cloneCommand.call();
                Log.d(LC, "Successfully cloned the remote repository");
            } catch (GitAPIException e) {
                return "Could not clone repository: " + e.getMessage();
            }
        }
        git.close();
        return null;
    }

    private String getLocalChanges() {
        Git git;
        try {
            git = Git.open(workDir.toFile());
        } catch (IOException e) {
            return "Git open error: " + e.getMessage();
        }
        Status gitStatus;
        try {
            gitStatus = git.status().call();
        } catch (GitAPIException e) {
            return "Git status error: " + e.getMessage();
        }
        return "Git status"
                + "\nAdded: " + gitStatus.getAdded()
                + "\nRemoved: " + gitStatus.getRemoved()
                + "\nChanged: " + gitStatus.getChanged()
                + "\nModified:" + gitStatus.getModified()
                + "\nUntracked" + gitStatus.getUntracked()
                + "\nUncommitted: " + gitStatus.getUncommittedChanges();
    }

    private String getPlaylists(Context context, boolean fetch, Consumer<PlaylistPath> consumer) {
        if (fetch) {
            String error = updateWorkDir();
            if (error != null) {
                return error;
            }
            Log.d(LC, "Local working tree is up to date. Traversing files...");
        }
        List<Path> playlistPaths;
        try (Stream<Path> walk = Files.walk(Paths.get(workDir.toUri()))) {
            playlistPaths = walk
                    .filter(Files::isRegularFile)
                    .filter(path -> HiddenFileFilter.VISIBLE.accept(path.toFile()))
                    .filter(path -> FilenameUtils.isExtension(path.toString(), SchemaValidator.PLAYLIST_SUFFIX))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return "Could not traverse repository"
                    + " for playlist files: " + e.getMessage();
        }
        Log.d(LC, "Valid playlist files:\n"
                + playlistPaths.stream()
                .map(Path::toString)
                .collect(Collectors.joining("\n"))
        );
        playlistPaths.forEach(path -> consumer.accept(new PlaylistPath(
                path,
                Playlist.from(context, src, path)))
        );
        return null;
    }

    @Override
    public CompletableFuture<Optional<List<Playlist>>> getPlaylists(
            Context context,
            APIClientRequestHandler handler
    ) {
        List<Playlist> playlists = new ArrayList<>();
        String error = getPlaylists(context, true, p -> playlists.add(p.playlist));
        if (error != null) {
            return Util.futureResult(error);
        }
        return Util.futureResult(Optional.of(playlists));
    }

    @Override
    public APIClient.Batch startBatch(Context context) throws BatchException {
        return new Batch(context, this);
    }

    public static class Batch extends APIClient.Batch {
        private final GitAPIClient api;
        private final HashMap<PlaylistID, PlaylistPath> playlistPaths = new HashMap<>();
        private final List<String> commitMessages = new ArrayList<>();

        Batch(Context context, GitAPIClient api) throws BatchException {
            this.api = api;
            updatePlaylistPaths(context, true);
        }

        private void updatePlaylistPaths(Context context, boolean fetch) throws BatchException {
            playlistPaths.clear();
            String error = api.getPlaylists(
                    context,
                    fetch,
                    p -> playlistPaths.put(p.playlist.id, p)
            );
            if (error != null) {
                throw new BatchException("Could not get playlists from local workdir: " + error);
            }
        }

        private Path getPath(PlaylistID playlistID) throws APIClient.BatchException {
            PlaylistPath playlistPath = playlistPaths.get(playlistID);
            if (playlistPath == null) {
                throw new BatchException("Could not find playlist with id " + playlistID.getDisplayableString());
            }
            return playlistPath.path;
        }

        @Override
        public void addPlaylist(Context context,
                                PlaylistID playlistID,
                                String name,
                                String query,
                                PlaylistID beforePlaylistID
        ) throws BatchException {
            if (playlistPaths.containsKey(playlistID)) {
                throw new BatchException("Playlist with id " + playlistID.getDisplayableString()
                + " already exists");
            }
            String fileName = playlistID.id + ".yaml";
            int counter = 1;
            while (true) {
                Path path = api.workDir.resolve(fileName);
                if (!playlistPaths.values().stream()
                        .map(p -> p.path)
                        .anyMatch(p -> p.equals(path))) {
                    break;
                }
                fileName = playlistID.id + "-" + counter + ".yaml";
            }
            Path path = api.workDir.resolve(fileName);
            String error = StupidPlaylist.addPlaylistInFile(
                    context,
                    path,
                    playlistID,
                    name,
                    query
            );
            if (error != null) {
                throw new BatchException("Could not update local workdir: " + error);
            }
            commitMessages.add(new TransactionPlaylistAdd(
                    0,
                    null,
                    null,
                    0,
                    null,
                    playlistID,
                    name,
                    query,
                    beforePlaylistID
            ).getDisplayableDetails());
            updatePlaylistPaths(context, false);
        }

        @Override
        public void deletePlaylist(Context context, PlaylistID playlistID) throws BatchException {
            if (!playlistPaths.containsKey(playlistID)) {
                throw new BatchException("Can not find playlist "
                        + playlistID.getDisplayableString());
            }
            PlaylistPath playlistPath = playlistPaths.get(playlistID);
            if (playlistPath == null) {
                throw new BatchException("Can not find path for playlist "
                        + playlistID.getDisplayableString());
            }
            File playlistFile = playlistPath.path.toFile();
            if (!playlistFile.isFile()) {
                throw new BatchException("Could not delete playlist "
                        + playlistID.getDisplayableString() + " in local workdir. Not a file.");
            }
            if (!playlistFile.delete()) {
                throw new BatchException("Could not delete playlist "
                        + playlistID.getDisplayableString() + " in local workdir");
            }
            commitMessages.add(new TransactionPlaylistDelete(
                    0,
                    null,
                    null,
                    0,
                    null,
                    playlistID
            ).getDisplayableDetails());
            updatePlaylistPaths(context, false);
        }

        @Override
        public void addPlaylistEntry(Context context,
                                     PlaylistID playlistID,
                                     EntryID entryID,
                                     String beforePlaylistEntryID,
                                     Meta metaSnapshot
        ) throws BatchException {
            String error = StupidPlaylist.addEntryInFile(
                    context,
                    getPath(playlistID),
                    entryID,
                    beforePlaylistEntryID,
                    metaSnapshot
            );
            if (error != null) {
                throw new BatchException("Could not update local workdir: " + error);
            }
            commitMessages.add(new TransactionPlaylistEntryAdd(
                    0,
                    null,
                    null,
                    0,
                    null,
                    playlistID,
                    entryID,
                    beforePlaylistEntryID,
                    metaSnapshot
            ).getDisplayableDetails());
        }

        @Override
        public void deletePlaylistEntry(Context context,
                                        PlaylistID playlistID,
                                        String playlistEntryID,
                                        EntryID entryID
        ) throws BatchException {
            String error = StupidPlaylist.deleteEntryInFile(
                    context,
                    getPath(playlistID),
                    playlistEntryID
            );
            if (error != null) {
                throw new BatchException("Could not update local workdir: " + error);
            }
            commitMessages.add(new TransactionPlaylistEntryDelete(
                    0,
                    null,
                    null,
                    0,
                    null,
                    playlistID,
                    playlistEntryID,
                    entryID
            ).getDisplayableDetails());
        }

        @Override
        public void movePlaylistEntry(Context context,
                                      PlaylistID playlistID,
                                      String playlistEntryID,
                                      EntryID entryID,
                                      String beforePlaylistEntryID
        ) throws BatchException {
            String error = StupidPlaylist.moveEntryInFile(
                    context,
                    getPath(playlistID),
                    playlistEntryID,
                    beforePlaylistEntryID
            );
            if (error != null) {
                throw new BatchException("Could not update local workdir: " + error);
            }
            commitMessages.add(new TransactionPlaylistEntryMove(
                    0,
                    null,
                    null,
                    0,
                    null,
                    playlistID,
                    playlistEntryID,
                    entryID,
                    beforePlaylistEntryID
            ).getDisplayableDetails());
        }

        @Override
        CompletableFuture<Void> commit() {
            if (commitMessages.isEmpty()) {
                return Util.futureResult();
            }
            String message;
            if (commitMessages.size() == 1) {
                message = commitMessages.get(0);
            } else {
                message = "Multiple changes (" + commitMessages.size() + ")\n\n";
                message += String.join("\n", commitMessages);
            }
            String error = api.pushWorkDir(message);
            if (error != null) {
                return Util.futureResult("Could not push changes: " + error);
            }
            return Util.futureResult();
        }
    }

    private String pushWorkDir(String message) {
        try {
            Git git = Git.open(workDir.toFile());
            git.add()
                    .addFilepattern(".")
                    .call();
            git.add()
                    .setUpdate(true)
                    .addFilepattern(".")
                    .call();
            if (git.status().call().isClean()) {
                return "No changes to commit";
            }
            git.commit()
                    .setAll(true)
                    .setAllowEmpty(false)
                    .setCommitter("dancingBunnies", "dancingBunnies@splushii.se")
                    .setMessage(message)
                    .call();
            git.push()
                    .setRemote(gitURI)
                    .setTransportConfigCallback(transportConfigCallback)
                    .call();

        } catch (IOException | GitAPIException e) {
            e.printStackTrace();
            return e.getMessage();
        }
        return null;
    }

    private static class PlaylistPath {
        final Path path;
        final Playlist playlist;
        PlaylistPath(Path path, Playlist playlist) {
            this.path = path;
            this.playlist = playlist;
        }
    }
}
