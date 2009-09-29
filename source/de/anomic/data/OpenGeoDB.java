// OpenGeoDB.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 04.10.2009 on http://yacy.net
//
// This is a part of YaCy
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * this class loads and parses database dumps from the OpenGeoDB project
 * files can be loaded from http://sourceforge.net/projects/opengeodb/files/
 * this class is used by the LibraryProvider, which expects input files inside
 * DATA\DICTIONARIES\source
 * 
 * ATTENTION:
 * if this class is used, expect an extra memory usage of more than 100MB!
 * 
 * This class will provide a super-fast access to the OpenGeoDB,
 * since all request are evaluated using data in the RAM.
 */
public class OpenGeoDB {
    
 // use a collator to relax when distinguishing between lowercase und uppercase letters
    private static final Collator insensitiveCollator = Collator.getInstance(Locale.US);
    static {
        insensitiveCollator.setStrength(Collator.SECONDARY);
        insensitiveCollator.setDecomposition(Collator.NO_DECOMPOSITION);
    }
    
    private HashMap<Integer, String>       locTypeHash2locType;
    private HashMap<Integer, Location>     id2loc;
    private HashMap<Integer, Integer>      id2locTypeHash;
    private TreeMap<String, List<Integer>> locationName2ids;
    private TreeMap<String, List<Integer>> kfz2ids;
    private HashMap<String, List<Integer>> predial2ids;
    private HashMap<String, Integer>       zip2id;
    
    public OpenGeoDB(final File file) {

        this.locTypeHash2locType = new HashMap<Integer, String>();
        this.id2loc              = new HashMap<Integer, Location>();
        this.id2locTypeHash      = new HashMap<Integer, Integer>();
        this.locationName2ids    = new TreeMap<String, List<Integer>>(insensitiveCollator);
        this.kfz2ids             = new TreeMap<String, List<Integer>>(insensitiveCollator);
        this.predial2ids         = new HashMap<String, List<Integer>>();
        this.zip2id              = new HashMap<String, Integer>();
        
        if (file == null || !file.exists()) return;
        BufferedReader reader = null;
        try {
            InputStream is = new FileInputStream(file);
            if (file.getName().endsWith(".gz")) is = new GZIPInputStream(is);
            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String line;
            
            // read lines
            String[] v;
            Integer id;
            String h;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("INSERT INTO ")) continue;
                if (!line.endsWith(");")) continue;
                line = line.substring(12);
                //p = line.indexOf(' '); if (p < 0) continue;
                if (line.startsWith("geodb_coordinates ")) {
                    line = line.substring(18 + 7);v = line.split(",");
                    v = line.split(",");
                    id2loc.put(Integer.parseInt(v[0]), new Location(Double.parseDouble(v[2]), Double.parseDouble(v[3])));
                }
                if (line.startsWith("geodb_textdata ")) {
                    line = line.substring(15 + 7);
                    v = line.split(",");
                    if (v[1].equals("500100000")) { // Ortsname
                        id = Integer.parseInt(v[0]);
                        h = removeQuotes(v[2]);
                        List<Integer> l = this.locationName2ids.get(h);
                        if (l == null) l = new ArrayList<Integer>(1);
                        l.add(id);
                        this.locationName2ids.put(h, l);
                        Location loc = this.id2loc.get(id);
                        if (loc != null) loc.setName(h);
                    } else if (v[1].equals("500400000")) { // Vorwahl
                        id = Integer.parseInt(v[0]);
                        h = removeQuotes(v[2]);
                        List<Integer> l = this.predial2ids.get(h);
                        if (l == null) l = new ArrayList<Integer>(1);
                        l.add(id);
                        this.predial2ids.put(h, l);
                    } else if (v[1].equals("400300000")) { // Ortstyp
                        id = Integer.parseInt(v[0]);
                        h = removeQuotes(v[2]);
                        Integer hc = h.hashCode();
                        String t = this.locTypeHash2locType.get(hc);
                        if (t == null) this.locTypeHash2locType.put(hc, h);
                        this.id2locTypeHash.put(id, hc);
                    } else if (v[1].equals("500300000")) { // PLZ
                        this.zip2id.put(removeQuotes(v[2]), Integer.parseInt(v[0]));
                    } else if (v[1].equals("500500000")) { // KFZ-Kennzeichen
                        id = Integer.parseInt(v[0]);
                        h = removeQuotes(v[2]);
                        List<Integer> l = this.kfz2ids.get(h);
                        if (l == null) l = new ArrayList<Integer>(1);
                        l.add(id);
                        this.kfz2ids.put(h, l);
                    }
                }
                continue;
            }
            reader.close();
        } catch (final IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) try { reader.close(); } catch (final Exception e) {}
        }
    }
 
    private static final String removeQuotes(String s) {
        if (s.charAt(0) != '\'') return s;
        if (s.charAt(s.length() - 1) != '\'') return s;
        s = s.substring(1, s.length() - 1);
        return s;
    }
    
    /**
     * check database tables against occurrences of this entity
     * the anyname - String may be one of:
     * - name of a town, villa, region etc
     * - zip code
     * - telephone prefix
     * - kfz sign
     * @param anyname
     * @return
     */
    public HashSet<Location> find(String anyname) {
        HashSet<Integer> r = new HashSet<Integer>();
        SortedMap<String, List<Integer>> cities = this.locationName2ids.tailMap(anyname);
        for (Map.Entry<String, List<Integer>> e: cities.entrySet()) {
        	if (e.getKey().toLowerCase().startsWith(anyname.toLowerCase())) r.addAll(e.getValue()); else break;
        }
        List<Integer> c;
        c = this.kfz2ids.get(anyname); if (c != null) r.addAll(c);
        c = this.predial2ids.get(anyname); if (c != null) r.addAll(c);
        Integer i = this.zip2id.get(anyname); if (i != null) r.add(i);
        HashSet<Location> a = new HashSet<Location>();
        for (Integer e: r) {
            Location w = this.id2loc.get(e);
            if (w != null) a.add(w);
        }
        return a;
    }
    
    /**
     * read the dictionary and construct a set of recommendations to a given string 
     * @param s input value that is used to match recommendations
     * @return a set that contains all words that start with the input value
     */
    public Set<String> recommend(String s) {
        Set<String> a = new HashSet<String>();
        s = s.trim().toLowerCase();
        SortedMap<String, List<Integer>> t = this.locationName2ids.tailMap(s);
        for (String r: t.keySet()) {
            if (r.startsWith(s)) a.add(r); else break;
        }
        return a;
    }
}
