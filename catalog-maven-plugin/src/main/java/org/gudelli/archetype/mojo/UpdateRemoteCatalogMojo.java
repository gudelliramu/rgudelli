package org.gudelli.archetype.mojo;

import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.name;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.maven.archetype.catalog.Archetype;
import org.apache.maven.archetype.catalog.ArchetypeCatalog;
import org.apache.maven.archetype.catalog.io.xpp3.ArchetypeCatalogXpp3Reader;
import org.apache.maven.archetype.catalog.io.xpp3.ArchetypeCatalogXpp3Writer;
import org.apache.maven.archetype.source.ArchetypeDataSourceException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.WagonException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.repository.Repository;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.WriterFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gudelli.archetype.PackagingType;
import org.gudelli.archetype.WagonProvider;
import org.gudelli.archetype.utils.FileUtils;
import org.twdata.maven.mojoexecutor.MojoExecutor;

@Mojo(name = "update-remote-catalog", defaultPhase = LifecyclePhase.DEPLOY)
public class UpdateRemoteCatalogMojo extends AbstractMojo {

    private static final Plugin WAGON_MAVEN_PLUGIN = MojoExecutor.plugin(groupId("org.codehaus.mojo"),
            artifactId("wagon-maven-plugin"), version("2.0.0"));
    private static final String CATALOG_FILE_NAME = "archetype-catalog";
    private static final String CATALOG_FILE_EXTENSION = ".xml";
    private static final String ARCHETYPE_CATALOG_FILENAME = CATALOG_FILE_NAME + CATALOG_FILE_EXTENSION;

    @Component(hint = "default")
    private LegacySupport legacySupport;

    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    @Parameter(property = "session", required = true, readonly = true)
    protected MavenSession session;

    /**
     * Set to true if authentication is required to upload the file.
     */
    @Parameter(property = "authenticationRequired", required = true, defaultValue = "true")
    protected String authenticationRequired;

    /**
     * serverId of the {@link #url}. This serverId value must match with the id
     * specified in settings.xml for the {@link #url}. If this value is not
     * specified, the ID from DistributionManagement element will be used. This
     * property value will be ignored if {@link #authenticationRequired} is false.
     */
    @Parameter(property = "serverId")
    protected String serverId;

    /**
     * URL of the Remote Repository. If not specified, the URL specified in the
     * DistributionManagement element.
     */
    @Parameter(property = "url")
    protected String url;

    @Component
    private BuildPluginManager pluginManager;

    /*
     * @Requirement private Map<String, Wagon> wagons;
     */
    @Component(hint = "default")
    private WagonProvider wagonProvider;

    @Component
    private SettingsDecrypter settingsDecrypter;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // TODO Auto-generated method stub

        if (!PackagingType.MAVEN_ARCHETYPE.getValue().contentEquals(this.project.getPackaging())) {
            throw new MojoFailureException(
                    "This plugin can be used only on " + PackagingType.MAVEN_ARCHETYPE.getValue() + " packaging.");
        }

        ArtifactRepository repo = null;
        if (repo == null) {
            repo = project.getDistributionManagementArtifactRepository();
        }
        if (repo == null) {
            String msg = "Uploading of " + ARCHETYPE_CATALOG_FILENAME
                    + " file failed. Repository element was not specified in the POM inside distributionManagement element.";

            throw new MojoExecutionException(msg);
        }

        if (serverId == null) {
            serverId = repo.getId();
        }
        if (BooleanUtils.toBoolean(authenticationRequired) && serverId == null) {
            throw new MojoFailureException(
                    "'serverId' element value must be set when 'authenticationRequired' value is true.");
        }

        if (url == null) {
            url = repo.getUrl();
        }

        Archetype archetype = new Archetype();
        archetype.setGroupId(this.project.getGroupId());
        archetype.setArtifactId(this.project.getArtifactId());
        archetype.setVersion(this.project.getVersion());
        if (StringUtils.isNotEmpty(this.project.getDescription())) {
            archetype.setDescription(this.project.getDescription());
        } else {
            archetype.setDescription(this.project.getName());
        }

        File tempCatalogFile = FileUtils.createTempFile(CATALOG_FILE_NAME, CATALOG_FILE_EXTENSION, null);
        tempCatalogFile.deleteOnExit();
        ArchetypeCatalog archetypeCatalog = null;
        try {
            archetypeCatalog = downloadCatalog(repo);
        } catch (Exception ex) {
            throw new MojoExecutionException("Unable to read " + ARCHETYPE_CATALOG_FILENAME + " from " + repo.getUrl(),
                    ex);
        }
        try {
            archetypeCatalog = updateCatalog(archetypeCatalog, archetype);
        } catch (ArchetypeDataSourceException ex) {
            throw new MojoExecutionException("Unable to update catalog.", ex);
        }
        try {
            createTempCatalogFile(archetypeCatalog, tempCatalogFile);
        } catch (ArchetypeDataSourceException ex) {
            throw new MojoExecutionException("Unable to create temporary catalog file.", ex);
        }
        String tempFilePath = null;
        try {
            tempFilePath = tempCatalogFile.getCanonicalPath();
        } catch (IOException e) {
            throw new MojoFailureException(
                    "Failed to get " + ARCHETYPE_CATALOG_FILENAME + " file path from temporary directory.");
        }

        executeMojo(
                WAGON_MAVEN_PLUGIN, goal("upload-single"), configuration(element(name("fromFile"), tempFilePath),
                        element(name("url"), url), element(name("serverId"), serverId)),
                executionEnvironment(project, session, pluginManager));

    }

    protected ArchetypeCatalog downloadCatalog(ArtifactRepository repository)
            throws WagonException, IOException, ArchetypeDataSourceException {
        if (getLog().isDebugEnabled()) {
            getLog().debug("Searching for remote catalog: " + repository.getUrl() + "/" + ARCHETYPE_CATALOG_FILENAME);
        }
        // We use wagon to take advantage of a Proxy that has already been setup in a
        // Maven environment.
        Repository wagonRepository = new Repository(repository.getId(), repository.getUrl());

        AuthenticationInfo authInfo = getAuthenticationInfo(wagonRepository.getId());
        ProxyInfo proxyInfo = getProxy(wagonRepository.getProtocol());

        Wagon wagon = wagonProvider.getWagon(wagonRepository);

        if (getLog().isDebugEnabled()) {
            getLog().debug("Wagon implementation class name:" + wagon.getClass().getName());
        }

        File catalog = File.createTempFile(CATALOG_FILE_NAME, CATALOG_FILE_EXTENSION);
        if (getLog().isDebugEnabled()) {
            getLog().debug("Created Temporary file at " + catalog.getCanonicalPath());
        }

        try {
            wagon.connect(wagonRepository, authInfo, proxyInfo);
            if (getLog().isDebugEnabled()) {
                getLog().debug("Connected to Wagon.");
            }
            wagon.get(ARCHETYPE_CATALOG_FILENAME, catalog);
            getLog().info("Fetched " + ARCHETYPE_CATALOG_FILENAME + " from " + repository.getUrl());

            return readCatalog(ReaderFactory.newXmlReader(catalog));
        } finally {
            disconnectWagon(wagon);
            catalog.delete();
        }
    }

    protected ArchetypeCatalog readCatalog(Reader reader) throws ArchetypeDataSourceException {
        try (Reader catReader = reader) {
            ArchetypeCatalogXpp3Reader catalogReader = new ArchetypeCatalogXpp3Reader();

            return catalogReader.read(catReader);
        } catch (IOException e) {
            throw new ArchetypeDataSourceException("Error reading archetype catalog.", e);
        } catch (XmlPullParserException e) {
            throw new ArchetypeDataSourceException("Error parsing archetype catalog.", e);
        }
    }

    private AuthenticationInfo getAuthenticationInfo(String id) {
        try {
            MavenSession session = legacySupport.getSession();

            if (session != null && id != null) {
                MavenExecutionRequest request = session.getRequest();

                if (request != null) {
                    List<Server> servers = request.getServers();

                    if (servers != null) {
                        for (Server server : servers) {
                            if (id.equalsIgnoreCase(server.getId())) {
                                SettingsDecryptionResult result = settingsDecrypter
                                        .decrypt(new DefaultSettingsDecryptionRequest(server));
                                server = result.getServer();

                                AuthenticationInfo authInfo = new AuthenticationInfo();
                                authInfo.setUserName(server.getUsername());
                                authInfo.setPassword(server.getPassword());
                                authInfo.setPrivateKey(server.getPrivateKey());
                                authInfo.setPassphrase(server.getPassphrase());

                                return authInfo;
                            }
                        }
                    }
                }
            }
        } catch (RuntimeException ex) {
            getLog().error(ex);
            throw ex;
        }
        // empty one to prevent NPE
        return new AuthenticationInfo();
    }

    private ProxyInfo getProxy(String protocol) {
        MavenSession session = legacySupport.getSession();

        if (session != null && protocol != null) {
            MavenExecutionRequest request = session.getRequest();

            if (request != null) {
                List<Proxy> proxies = request.getProxies();

                if (proxies != null) {
                    for (Proxy proxy : proxies) {
                        if (proxy.isActive() && protocol.equalsIgnoreCase(proxy.getProtocol())) {
                            SettingsDecryptionResult result = settingsDecrypter
                                    .decrypt(new DefaultSettingsDecryptionRequest(proxy));
                            proxy = result.getProxy();

                            ProxyInfo proxyInfo = new ProxyInfo();
                            proxyInfo.setHost(proxy.getHost());
                            proxyInfo.setType(proxy.getProtocol());
                            proxyInfo.setPort(proxy.getPort());
                            proxyInfo.setNonProxyHosts(proxy.getNonProxyHosts());
                            proxyInfo.setUserName(proxy.getUsername());
                            proxyInfo.setPassword(proxy.getPassword());

                            return proxyInfo;
                        }
                    }
                }
            }
        }

        return null;
    }

    /*
     * private Wagon getWagon(Repository repository) throws
     * UnsupportedProtocolException { return getWagon(repository.getProtocol()); }
     * 
     * private Wagon getWagon(String protocol) throws UnsupportedProtocolException {
     * if (protocol == null) { throw new
     * UnsupportedProtocolException("Unspecified protocol"); }
     * 
     * String hint = protocol.toLowerCase(java.util.Locale.ENGLISH);
     * 
     * Wagon wagon = wagons.get(hint); if (wagon == null) { throw new
     * UnsupportedProtocolException(
     * "Cannot find wagon which supports the requested protocol: " + protocol); }
     * 
     * return wagon; }
     */

    private void disconnectWagon(Wagon wagon) {
        try {
            wagon.disconnect();
        } catch (Exception e) {
            getLog().warn("Problem disconnecting from wagon - ignoring: " + e.getMessage());
        }
    }

    public ArchetypeCatalog updateCatalog(ArchetypeCatalog catalog, Archetype archetype)
            throws ArchetypeDataSourceException {

        Iterator<Archetype> archetypes = catalog.getArchetypes().iterator();
        boolean found = false;
        Archetype newArchetype = archetype;
        while (!found && archetypes.hasNext()) {
            Archetype a = archetypes.next();
            if (a.getGroupId().equals(archetype.getGroupId()) && a.getArtifactId().equals(archetype.getArtifactId())
                    && a.getVersion().equals(archetype.getVersion())) {
                newArchetype = a;
                newArchetype.setRepository(archetype.getRepository());
                newArchetype.setDescription(archetype.getDescription());
                found = true;
            }
        }

        catalog.addArchetype(newArchetype);

        return catalog;
    }

    protected void createTempCatalogFile(ArchetypeCatalog catalog, File tempCatalogFile)
            throws ArchetypeDataSourceException {

        try (Writer writer = WriterFactory.newXmlWriter(tempCatalogFile)) {
            ArchetypeCatalogXpp3Writer catalogWriter = new ArchetypeCatalogXpp3Writer();

            catalogWriter.write(writer, catalog);
        } catch (IOException e) {
            throw new ArchetypeDataSourceException("Error writing archetype catalog.", e);
        }
    }

}
