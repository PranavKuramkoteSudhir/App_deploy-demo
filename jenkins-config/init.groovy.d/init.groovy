import jenkins.model.*
import hudson.security.*
import hudson.util.*
import jenkins.install.*
import hudson.model.*
import jenkins.security.s2m.AdminWhitelistRule
import hudson.security.csrf.DefaultCrumbIssuer
import java.util.logging.Logger

def logger = Logger.getLogger("")
def instance = Jenkins.getInstance()
def pm = instance.getPluginManager()
def uc = instance.getUpdateCenter()
uc.updateAllSites()

def pluginsToInstall = [
    "git",                          // For Git integration
    "github",                       // For GitHub integration
    "workflow-aggregator",          // Pipeline plugin
    "docker-workflow",              // Docker Pipeline
    "docker-plugin",                // Docker plugin
    "credentials-binding"           // For handling credentials
]

def failedPlugins = []

// Install plugins
pluginsToInstall.each { pluginName ->
    logger.info("Checking " + pluginName)
    if (!pm.getPlugin(pluginName)) {
        logger.info("Looking up " + pluginName)
        def plugin = uc.getPlugin(pluginName)
        if (plugin) {
            logger.info("Installing " + pluginName)
            def installFuture = plugin.deploy()
            while(!installFuture.isDone()) {
                logger.info("Waiting for plugin " + pluginName + " to install...")
                sleep(3000)
            }
            logger.info("Plugin " + pluginName + " installed.")
        } else {
            failedPlugins.add(pluginName)
            logger.warning("Plugin " + pluginName + " not found!")
        }
    } else {
        logger.info("Plugin " + pluginName + " already installed.")
    }
}

// Skip setup wizard
instance.setInstallState(InstallState.INITIAL_SETUP_COMPLETED)

// Create admin user
def hudsonRealm = new HudsonPrivateSecurityRealm(false)
def adminUsername = "admin"
def adminPassword = "admin"

// Only create the admin user if it doesn't exist
if (hudsonRealm.getAllUsers().find { it.id == adminUsername } == null) {
    hudsonRealm.createAccount(adminUsername, adminPassword)
    logger.info("Admin user created")
}

instance.setSecurityRealm(hudsonRealm)

// Configure authorization
def strategy = new FullControlOnceLoggedInAuthorizationStrategy()
strategy.setAllowAnonymousRead(false)
instance.setAuthorizationStrategy(strategy)

// Enable CSRF protection
instance.setCrumbIssuer(new DefaultCrumbIssuer(true))

// Enable agent to master security subsystem
instance.getInjector().getInstance(AdminWhitelistRule.class).setMasterKillSwitch(false)

// Disable CLI
def cli = instance.getDescriptorByType(jenkins.CLI.CLIConfiguration.class)
if (cli != null) {
    cli.setEnabled(false)
}

// Save configuration
instance.save()

// Log results
if (failedPlugins.size() > 0) {
    logger.warning("Failed plugins: " + failedPlugins)
} else {
    logger.info("All plugins installed successfully")
}

// Handle restart if required
if (uc.isRestartRequiredForCompletion()) {
    logger.info("Restart required")
    instance.save()
    System.setProperty("jenkins.install.runSetupWizard", "false")
    instance.doQuietDown()
}