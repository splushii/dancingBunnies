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
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
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

    private Path workDir;
    private String gitURI;
    private String gitBranch;
    private String sshPrivKey;
    private String sshPassphrase;
    private String httpUser;
    private String httpPassphrase;

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
        // TODO: Use settings bundle
        this.workDir = workDir;
        gitURI = "ssh://git@github.com/splushii/dbtest.git";
        gitBranch = "master";
        sshPrivKey = "-----BEGIN RSA PRIVATE KEY-----\n"
                + "Proc-Type: 4,ENCRYPTED\n"
                + "DEK-Info: AES-128-CBC,9EFDFB6AB6C1C397ADDF5F2411569FDF\n"
                + "\n"
                + "ydwye6MLXGrbVjXX9Ppb8aCmpSZWZvjCCDuBX5t9kPcG4JWK7hjhunKzDoPa9xBm\n"
                + "1pZ8Pga1cjiQcfxdV/z40g+ZqNmiXbBla5+hPZ2GrgIY+vGYXFladBMrmmU/52K9\n"
                + "fDB9yNP6VjEelCCZaNKj+CvGPCJV0AXA9SmO/1UMJye3GkBYWXGJ4bk8rftXfhbx\n"
                + "gF9rKFkVfBtfmhIlT1v0M+oERofA7vWgZqeSz57nPygG/DT6jPeReYL+AFBEj6to\n"
                + "juOyPCOB2Ptv6Wft+7z+8MogDeonxD4EVDGvfUTNmnX3a+bTuIYqr+kEDhJOjnAP\n"
                + "lGZWF9vIeUsCU3usZ8cs350VsLqkfTZKvznXFHlW8TVML/zQkKtVDcKAhyDI4HFp\n"
                + "YWHEbLfj9JFJGQPAk3upOF7+Cs63w+FCAwddDGZB3p5aQ9OP/ui0PARkHFKg31Az\n"
                + "KrmxLudpzDppzJAhJ3tjimyV1F8FGx3MQ6soDbC+9VlsNOkF1AjPkiyDli1Hk+um\n"
                + "e0D9vym5p7REyDzJtZpOnx7dT1Qf5EQZnzQmhA+wairVJlAHtEuM+cLJgw9936p4\n"
                + "zf/jVkGd63kEvVy+dTp+RvytX5rPm0+HVjj4kdsv2o4e+2k7sJypp8YvidgbljAg\n"
                + "OgGSrDsX1vI3Z+rZbU+RKrJ/ELHZOoTuhuUcbrGu+xxw/kUJdWQMj8Xu+ghXr5ah\n"
                + "8JTmFOhDpFQ6N38+9kIQIdrzqBXesbT2pILHfANuj5TvUIywvtXtIgTaviUmKfSw\n"
                + "tqItCLHE9khFVejGOH0VV+L4xdO979MVvZczdgZTdCFuUeJcpYXLMQrgwyeAm9gL\n"
                + "3nLFSwjZNjDgahzY3AcE5qpa9bZTgpig5GOr92sNTxqCeD7K/zujcFmMVW4O3s69\n"
                + "Ul3yWj7lXQBVPNGg31oLlAvtXMwb2JKmez646xNB7gNYRV+2A9xQ6ly2AjxavXYZ\n"
                + "cqe0+rQslgQNP9tkHgAkcZAP3bujd2CsF4F+1kih9iv/BACsv62KU1Bx4Imwe3FV\n"
                + "zq1ZI8aa8zJf/7pfPEpO+xMlsQyZ+vaEnhaSehJxELF3LVpnyppFh6QYQgAkvmzv\n"
                + "OVQJB1jPDqenWsMBmaBvr1OKVoTQGX4rvA2FhhI4fBOhAzYvt8sYaznAMSzMo+ae\n"
                + "4AA7v0kMb7uc+dycKA1VGhESVl1N/7UUUIoj09KdSDHbQ7fculzMR6WtkIOBgAw/\n"
                + "5v5GOMG3qPQKp61zSnTNGTWSxhiQHdS+W1FZcQRkcI5K2EaI3i/K/7GrvnW1J/rs\n"
                + "JiSiP+SktyRmfz5THaWrLPSjImW1JdSOv/6EZ2vDjRoZQm8b3fYo2whLbAqXEmbX\n"
                + "uK//InqY1Xu8c7G/6HgCI2/aRNkpXZet5N9MeCrv6NqnsrDamnCE2xcjkmsMLfFf\n"
                + "WE4bLgVhvzOE4hvm1ItgtEoUlI0hKiGwJkkP19LQ48oCzSuQOdbb/qCReSlTL5H+\n"
                + "xGocvjgfcA/lq8/kA9Ud0gVl4oNWN03q8Z4IaGxSBaCmZcsTu545tTkz2wDfzDWU\n"
                + "14MDbW1WZxTfqFR2Qfstj1mR29t4Q73+Jhp5E8WEYS1W/f+LwXttCYUEx7FkexMR\n"
                + "5yk2tvMa+Y5FzbGljuQfhZKxQ58uv5RbnLEOblbzftSIJEbujmbMAQOBUVrnqiJf\n"
                + "MqMPlYHFe6aUiCYS7vaqinCkb1yCy16SSLv03BHp4C9sg3I9GiUmGXAbp9RB5Gf4\n"
                + "FrnUeNHaPqvT0uwaAcEHDMqryL86PhMlPResMwJkhHgPGAONUIgAHeDsV099J1IC\n"
                + "iKT+KQd8Oso1OXcRhO/uvqwtICPrXPWtohZLKcQVP0GjLGH8AjCj1RTwDBbfoFCY\n"
                + "l8DeKqR7bNvphTSQqP5Ki3T67IR85MrJlHFwqz88MVIGl4dhuHavlNpZDbZonAB9\n"
                + "L0PBR/SLdga+Vcgjje5AgN5mJYsF2cZRGtFL9HF5Mv4QD9oDIfhtG78Bu88L6Rzu\n"
                + "uw0l9fzten7M2fvzxcyEx8d+AOVrAsrEG7+EPXhlr3frxLlljOvFDN6lZRLinvRO\n"
                + "oVpTuEEsHwfie0m4di5iktlJDBWr8q4upBNupi0fgq+3q4DMt8e2Vz/6YUt6wyg4\n"
                + "RrF3kXPUv+aZz33rB1+L8HOyiFWaFBT0qlf+TdWZGbPDiJ5wF1IMFXWvBwYQUdLg\n"
                + "GNA0p2TS/GNcleGI4wk2FoNbZ5D3inRBigsMcej2YyjedSkVN/1xPe6u1igzT2GV\n"
                + "xpxcOPqU9ARxNY/qmR5cpBg+RPk/fkySuhtv8ogXW9N4l7msJbLlcAc48FBOnmBi\n"
                + "tHNR2sDkgfTo/Ukz2ltZxmanifheNifua5Gmv0Jn87ItCF9uzo821CYAT1tJZSF4\n"
                + "-----END RSA PRIVATE KEY-----";
        sshPassphrase = "testa";
    }

    @Override
    public CompletableFuture<Optional<String>> heartbeat() {
        return null;
    }

    private void configureSSHTransport(Transport transport) {
        SshTransport sshTransport = (SshTransport) transport;
        sshTransport.setSshSessionFactory(new JschConfigSessionFactory() {
            @Override
            protected void configure(OpenSshConfig.Host hc, Session session) {
                // TODO: Enable if possible (use Android bundled CA:s?)
                session.setConfig("StrictHostKeyChecking", "no");
            }

            @Override
            protected JSch createDefaultJSch(FS fs) throws JSchException {
                JSch jSch = super.createDefaultJSch(fs);
                jSch.addIdentity(
                        "id",
                        sshPrivKey.getBytes(),
                        null,
                        sshPassphrase == null ? null : sshPassphrase.getBytes()
                );
                return jSch;
            }
        });
    }

    @Override
    public CompletableFuture<Optional<List<Playlist>>> getPlaylists(APIClientRequestHandler handler) {
//        String pubKey = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQC4uhvAWB+/0lLpL1yqabshlCBirpyh0xEzT49Cbb6PMTcmg00X7ZGALxR/yyeAU6WwhMs7/kcWAUd6GKMnpYrx/+NIG2VKiDWvMUSZG4DHnoZbw2lvN83s3aIzrTmSfpCpTA5Gt//521yIIu1G+g0bACmAxH409C1u+lNse1efmjyn1J7wtSIRM4TaAsNxL6HmqShNHWK96+qMvYgVdXmPChfWUpTfefkyH3LhyFAGwFdRVZLY2sjEG9C2GkCPDlGIlYvhYvRqe0Vif8qazj6UuynjlcCPpOjclIaxkcT2t9ifgWN+Ac8O4k/dpM+mUBlTKhQq1bnzp8kYTKuo496BBqfLtCAC/NjXZId6itq8vsq9eouXClpAH9RBI90qU02WXn/KWHdAWVTljDrn2lQCjuRYuPkKSfZ/6A5I0xw14lQIXS17GLH6DydxLr3qIYu7oIvh5cPNhb1aXBuItANS8Rq2QeDr/CCh79ZRJKU8d01jkeXzCqrP+ZeRvVoUDMc= c@r";
//        String passphrase = "testa";
//        String uri = "ssh://git@github.com/splushii/dbtest.git";
//        String branch = "master";
        TransportConfigCallback transportConfigCallback = transport -> {
            if (sshPrivKey != null) {
                configureSSHTransport(transport);
            } else if (httpUser != null){
                // TODO: Support HTTPS user/pass transport
            } else {
                Log.e(LC, "getPlaylists: No input to configure Git transport");
            }
        };

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
            String currentBranch;
            try {
                Log.d(LC, "getPlaylists: Using existing repository at: " + workDir.toString());
                git = Git.open(workDir.toFile());
                currentBranch = git.getRepository().getBranch();
            } catch (IOException e) {
                e.printStackTrace();
                return Util.futureResult(
                        "getPlaylists: Could not reuse repository: " + workDir.toString()
                                + " : " + e.getMessage()
                );
            }
            if (!currentBranch.equals(gitBranch)) {
                Log.d(LC, "Current branch \"" + currentBranch + "\""
                        + " does not match target branch \"" + gitBranch + "\"."
                        + " Checking out target branch.");
                try {
                    git.checkout()
                            .setName("origin/" + gitBranch)
                            .setCreateBranch(true)
                            .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                            .setStartPoint("origin/" + gitBranch)
                            .call();
                } catch (GitAPIException e) {
                    e.printStackTrace();
                    return Util.futureResult(
                            "getPlaylists: Could not checkout target branch: " + e.getMessage()
                    );
                }
                Log.d(LC, "Successfully checked out target branch \"" + gitBranch + "\"");
            }
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
                e.printStackTrace();
                return Util.futureResult(
                        "getPlaylists: Could not pull and rebase against "
                                + "\"" + gitBranch + "\": " + e.getMessage()
                );
            }
        }
        if (git == null) {
            Log.d(LC, "Cloning repository");
            CloneCommand cloneCommand = Git.cloneRepository()
                    .setDirectory(workDir.toFile())
                    .setTransportConfigCallback(transportConfigCallback)
                    .setBranch(gitBranch)
                    .setURI(gitURI);
            try {
                git = cloneCommand.call();
            } catch (GitAPIException e) {
                e.printStackTrace();
                return Util.futureResult(
                        "getPlaylists: Could not clone repository: " + e.getMessage()
                );
            }
        }
        List<Path> playlistPaths;
        try (Stream<Path> walk = Files.walk(Paths.get(workDir.toUri()))) {
            playlistPaths = walk
                    .filter(Files::isRegularFile)
                    .filter(path -> HiddenFileFilter.VISIBLE.accept(path.toFile()))
                    .filter(path -> FilenameUtils.isExtension(path.toString(), SchemaValidator.PLAYLIST_SUFFIX))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            git.close();
            return Util.futureResult("getPlaylists: Could not traverse repository"
                    + " for playlist files: " + e.getMessage());
        }
        Log.d(LC, "Valid playlist files:\n"
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
