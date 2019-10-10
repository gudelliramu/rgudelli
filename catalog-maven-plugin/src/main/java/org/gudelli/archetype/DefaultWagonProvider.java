package org.gudelli.archetype;

import java.util.Map;

import org.apache.maven.wagon.UnsupportedProtocolException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.repository.Repository;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

@Component(role = WagonProvider.class, hint = "default")
public class DefaultWagonProvider implements WagonProvider {

    @Requirement
    private Map<String, Wagon> wagons;

    /*
     * @Override public Wagon getWagon(String protocol) { // TODO Auto-generated
     * method stub return null; }
     * 
     * @Override public Wagon getWagon(Repository repository) { // TODO
     * Auto-generated method stub return null; }
     */
    
    @Override
    public  Wagon getWagon(Repository repository) throws UnsupportedProtocolException {
        return getWagon(repository.getProtocol());
    }

    @Override
    public  Wagon getWagon(String protocol) throws UnsupportedProtocolException {
        if (protocol == null) {
            throw new UnsupportedProtocolException("Unspecified protocol");
        }

        String hint = protocol.toLowerCase(java.util.Locale.ENGLISH);

        Wagon wagon = wagons.get(hint);
        if (wagon == null) {
            throw new UnsupportedProtocolException(
                    "Cannot find wagon which supports the requested protocol: " + protocol);
        }

        return wagon;
    }

}
