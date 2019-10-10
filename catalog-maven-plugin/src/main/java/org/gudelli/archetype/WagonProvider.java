package org.gudelli.archetype;

import org.apache.maven.wagon.UnsupportedProtocolException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.repository.Repository;

public interface WagonProvider {

    Wagon getWagon(String protocol) throws UnsupportedProtocolException;

    Wagon getWagon(Repository repository) throws UnsupportedProtocolException;

}
