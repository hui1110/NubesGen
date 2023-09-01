package io.github.nubesgen.utils;

import io.github.nubesgen.model.azure.springapps.JavaMavenProject;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

/**
 * Utility class that provides Java project related operations.
 */
public final class JavaProjectUtils {

    /**
     * Get project name and java version from pom.xml.
     *
     * @param url the GitHub repository url.
     * @param branchName the branch name.
     * @param module the module name.
     * @return project name and java version.
     */
    public static synchronized JavaMavenProject getNameAndJavaVersion(String url, String branchName, String module) {
        String pathName = GithubUtils.downloadSourceCodeFromGitHub(url, branchName).get("pathName");
        Model model;
        try {
            if (Objects.equals(module, null)) {
                try (FileInputStream fis = new FileInputStream(pathName.concat("/pom.xml"))) {
                    MavenXpp3Reader reader = new MavenXpp3Reader();
                    model = reader.read(fis);
                }
            } else {
                try (FileInputStream fis = new FileInputStream(pathName.concat("/").concat(module.concat("/pom.xml")))) {
                    MavenXpp3Reader reader = new MavenXpp3Reader();
                    model = reader.read(fis);
                    if (model.getProperties().isEmpty() || !model.getProperties().containsKey("java.version")) {
                        FileInputStream fisParent = new FileInputStream(pathName.concat("/pom.xml"));
                        MavenXpp3Reader readerParent = new MavenXpp3Reader();
                        Properties properties = readerParent.read(fisParent).getProperties();
                        fisParent.close();
                        if (!properties.isEmpty() && properties.containsKey("java.version")) {
                            model.getProperties().put("java.version", properties.getProperty("java.version"));
                        }
                    }
                }
            }
        }catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            GithubUtils.deleteRepositoryDirectory(pathName);
        }
        JavaMavenProject javaMavenProject = new JavaMavenProject();
        if (model.getName() != null) {
            javaMavenProject.setName(model.getName().replaceAll(" ", "").toLowerCase());
        }
        model.getProperties().put("java.version", model.getProperties().getOrDefault("java.version", "11"));
        javaMavenProject.setVersion("Java_".concat(String.valueOf(model.getProperties().get("java.version"))));
        return javaMavenProject;
    }


    /**
     * User source code to create tar.gz file.
     *
     * @param url the GitHub repository url.
     * @param branchName the branch name.
     * @return the tar.gz file.
     */
    public static synchronized File createTarGzFile(String url, String branchName) throws IOException {
        String pathName = GithubUtils.downloadSourceCodeFromGitHub(url, branchName).get("pathName");
        File sourceFolder = new File(pathName);
        File compressFile = File.createTempFile("java_package", "tar.gz");
        compressFile.deleteOnExit();
        try (TarArchiveOutputStream tarArchiveOutputStream = new TarArchiveOutputStream(
                new GZIPOutputStream(new FileOutputStream(compressFile)));
             Stream<Path> paths = Files.walk(sourceFolder.toPath())) {
            tarArchiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            for (Path sourceFile : paths.toList()) {
                String relativePath = sourceFolder.toPath().relativize(sourceFile).toString();
                TarArchiveEntry entry = new TarArchiveEntry(sourceFile.toFile(), relativePath);
                if (sourceFile.toFile().isFile()) {
                    try (InputStream inputStream = new FileInputStream(sourceFile.toFile())) {
                        tarArchiveOutputStream.putArchiveEntry(entry);
                        IOUtils.copy(inputStream, tarArchiveOutputStream);
                        tarArchiveOutputStream.closeArchiveEntry();
                    }
                } else {
                    tarArchiveOutputStream.putArchiveEntry(entry);
                    tarArchiveOutputStream.closeArchiveEntry();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            GithubUtils.deleteRepositoryDirectory(sourceFolder.getPath());
        }
        return compressFile;
    }

}
