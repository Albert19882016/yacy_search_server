// indexDefaultReference.java
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 11.07.2005 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package de.anomic.data;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class DefaultBlacklist extends AbstractBlacklist implements Blacklist {

    public DefaultBlacklist(final File rootPath) {
        super(rootPath);
    }

    public String getEngineInfo() {
        return "Default YaCy Blacklist Engine";
    }

    public boolean isListed(final String blacklistType, final String hostlow, String path) {
        if (hostlow == null) throw new NullPointerException();
        if (path == null) throw new NullPointerException();

        // getting the proper blacklist
        final HashMap<String, ArrayList<String>> blacklistMapMatched = super.getBlacklistMap(blacklistType,true);

        if (path.length() > 0 && path.charAt(0) == '/') path = path.substring(1);
        ArrayList<String> app;
        boolean matched = false;
        String pp = ""; // path-pattern

        // try to match complete domain
        if (!matched && (app = blacklistMapMatched.get(hostlow)) != null) {
            for (int i=app.size()-1; !matched && i>-1; i--) {
                pp = app.get(i);
                matched |= ((pp.equals("*")) || (path.matches(pp)));
            }
        }
        // first try to match the domain with wildcard '*'
        // [TL] While "." are found within the string
        int index = 0;
        while (!matched && (index = hostlow.indexOf('.', index + 1)) != -1) {
            if ((app = blacklistMapMatched.get(hostlow.substring(0, index + 1) + "*")) != null) {
                for (int i=app.size()-1; !matched && i>-1; i--) {
                    pp = app.get(i);
                    matched |= ((pp.equals("*")) || (path.matches(pp)));
                }
            }
            if ((app = blacklistMapMatched.get(hostlow.substring(0, index))) != null) {
                for (int i=app.size()-1; !matched && i>-1; i--) {
                    pp = app.get(i);
                    matched |= ((pp.equals("*")) || (path.matches(pp)));
                }
            }
        }
        index = hostlow.length();
        while (!matched && (index = hostlow.lastIndexOf('.', index - 1)) != -1) {
            if ((app = blacklistMapMatched.get("*" + hostlow.substring(index, hostlow.length()))) != null) {
                for (int i=app.size()-1; !matched && i>-1; i--) {
                    pp = app.get(i);
                    matched |= ((pp.equals("*")) || (path.matches(pp)));
                }
            }
            if ((app = blacklistMapMatched.get(hostlow.substring(index +1, hostlow.length()))) != null) {
                for (int i=app.size()-1; !matched && i>-1; i--) {
                    pp = app.get(i);
                    matched |= ((pp.equals("*")) || (path.matches(pp)));
                }
            }
        }


        // loop over all Regexentrys
        if(!matched) {
            final HashMap<String, ArrayList<String>> blacklistMapNotMatched = super.getBlacklistMap(blacklistType,false);
            String key;
            for(final Entry<String, ArrayList<String>> entry: blacklistMapNotMatched.entrySet()) {
                key = entry.getKey();
                try {
                    if(Pattern.matches(key, hostlow)) {
                        app = entry.getValue();
                        for (int i=0; i<app.size(); i++) {
                            if(Pattern.matches(app.get(i), path)) 
                                return true;
                        }
                    }
                } catch (final PatternSyntaxException e) {
                    //System.out.println(e.toString());
                }
            }
        }
        return matched;
    }

    public int checkError(String element, Map<String, String> properties) {

        boolean allowRegex = true;
        int slashPos;
        String host, path;

        if (properties != null) {
            allowRegex = properties.get("allowRegex").equalsIgnoreCase("true") ? true : false;
        }

        if ((slashPos = element.indexOf("/")) == -1) {
            host = element;
            path = ".*";
        } else {
            host = element.substring(0, slashPos);
            path = element.substring(slashPos + 1);
        }

        if (!allowRegex || !isValidRegex(host)) {
            final int i = host.indexOf("*");

            // check whether host begins illegally
            if (!host.matches("([A-Za-z0-9_-]+|\\*)(\\.([A-Za-z0-9_-]+|\\*))*")) {
                if (i == 0 && host.length() > 1 && host.charAt(1) != '.') {
                    return ERR_SUBDOMAIN_XOR_WILDCARD;
                }
                return ERR_HOST_WRONG_CHARS;
            }

            // in host-part only full sub-domains may be wildcards
            if (host.length() > 0 && i > -1) {
                if (!(i == 0 || i == host.length() - 1)) {
                    return  ERR_WILDCARD_BEGIN_OR_END;
                }

                if (i == host.length() - 1 && host.length() > 1 && host.charAt(i - 1) != '.') {
                    return ERR_SUBDOMAIN_XOR_WILDCARD;
                }
            }

            // check for double-occurences of "*" in host
            if (host.indexOf("*", i + 1) > -1) {
                return ERR_TWO_WILDCARDS_IN_HOST;
            }
        } else if (allowRegex && !isValidRegex(host)) {
            return ERR_HOST_REGEX;
        }

        // check for errors on regex-compiling path
        if (!isValidRegex(path) && !path.equals("*")) {
            return ERR_PATH_REGEX;
        }

        return 0;
    }

    /**
     * Checks if a given expression is a valid regular expression.
     * @param expression The expression to be checked.
     * @return True if the expression is a valid regular expression, else false.
     */
    private static boolean isValidRegex(String expression) {
        boolean ret = true;
        try {
            Pattern.compile(expression);
        } catch (final PatternSyntaxException e) {

            ret = false;
        }
        return ret;
    }

}
