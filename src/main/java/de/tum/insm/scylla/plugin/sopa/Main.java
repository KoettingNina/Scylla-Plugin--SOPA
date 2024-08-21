package de.tum.insm.scylla.plugin.sopa;

import de.hpi.bpt.scylla.Scylla;
import de.hpi.bpt.scylla.plugin_loader.PluginLoader;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        PluginLoader.getDefaultPluginLoader().loadPackage(Main.class.getPackageName());
        Scylla.main(args);
    }
}
