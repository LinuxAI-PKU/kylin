/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.apache.kylin.dict;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.TreeSet;

import org.junit.Test;

public class TrieDictionaryTest {

    public static void main(String[] args) throws Exception {
        int count = (int) (Integer.MAX_VALUE * 0.8 / 64);
        benchmarkStringDictionary(new RandomStrings(count));
    }

    private static class RandomStrings implements Iterable<String> {
        final private int size;

        public RandomStrings(int size) {
            this.size = size;
            System.out.println("size = " + size);
        }

        @Override
        public Iterator<String> iterator() {
            return new Iterator<String>() {
                Random rand = new Random(1000);
                int i = 0;

                @Override
                public boolean hasNext() {
                    return i < size;
                }

                @Override
                public String next() {
                    if (hasNext() == false)
                        throw new NoSuchElementException();

                    i++;
                    if (i % 1000000 == 0)
                        System.out.println(i);

                    return nextString();
                }

                private String nextString() {
                    StringBuffer buf = new StringBuffer();
                    for (int i = 0; i < 64; i++) {
                        int v = rand.nextInt(16);
                        char c;
                        if (v >= 0 && v <= 9)
                            c = (char) ('0' + v);
                        else
                            c = (char) ('a' + v - 10);
                        buf.append(c);
                    }
                    return buf.toString();
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    @Test
    public void partOverflowTest() {
        ArrayList<String> str = new ArrayList<String>();
        // str.add("");
        str.add("part");
        str.add("par");
        str.add("partition");
        str.add("party");
        str.add("parties");
        str.add("paint");
        String longStr = "paintjkjdfklajkdljfkdsajklfjklsadjkjekjrklewjrklewjklrjklewjkljkljkljkljweklrjewkljrklewjrlkjewkljrkljkljkjlkjjkljkljkljkljlkjlkjlkjljdfadfads" + "dddddddddddddddddddddddddddddddddddddddddddddddddkfjadslkfjdsakljflksadjklfjklsjfkljwelkrjewkljrklewjklrjelkwjrklewjrlkjwkljerklkljlkjrlkwejrk" + "dddddddddddddddddddddddddddddddddddddddddddddddddkfjadslkfjdsakljflksadjklfjklsjfkljwelkrjewkljrklewjklrjelkwjrklewjrlkjwkljerklkljlkjrlkwejrk" + "dddddddddddddddddddddddddddddddddddddddddddddddddkfjadslkfjdsakljflksadjklfjklsjfkljwelkrjewkljrklewjklrjelkwjrklewjrlkjwkljerklkljlkjrlkwejrk" + "dddddddddddddddddddddddddddddddddddddddddddddddddkfjadslkfjdsakljflksadjklfjklsjfkljwelkrjewkljrklewjklrjelkwjrklewjrlkjwkljerklkljlkjrlkwejrk" + "dddddddddddddddddddddddddddddddddddddddddddddddddkfjadslkfjdsakljflksadjklfjklsjfkljwelkrjewkljrklewjklrjelkwjrklewjrlkjwkljerklkljlkjrlkwejrk"
                + "dddddddddddddddddddddddddddddddddddddddddddddddddkfjadslkfjdsakljflksadjklfjklsjfkljwelkrjewkljrklewjklrjelkwjrklewjrlkjwkljerklkljlkjrlkwejrk" + "dddddddddddddddddddddddddddddddddddddddddddddddddkfjadslkfjdsakljflksadjklfjklsjfkljwelkrjewkljrklewjklrjelkwjrklewjrlkjwkljerklkljlkjrlkwejrk";
        System.out.println("The length of the long string is " + longStr.length());
        str.add(longStr);

        str.add("zzzzzz" + longStr);// another long string

        TrieDictionaryBuilder<String> b = newDictBuilder(str);
        TrieDictionary<String> dict = b.build(0);

        TreeSet<String> set = new TreeSet<String>();
        for (String s : str) {
            set.add(s);
        }

        // test serialize
        dict = testSerialize(dict);

        // test basic id<==>value
        Iterator<String> it = set.iterator();
        int id = 0;
        int previousId = -1;
        for (; it.hasNext(); id++) {
            String value = it.next();

            // in case of overflow parts, there exist interpolation nodes
            // they exist to make sure that any node's part is shorter than 255
            int actualId = dict.getIdFromValue(value);
            assertTrue(actualId >= id);
            assertTrue(actualId > previousId);
            previousId = actualId;

            assertEquals(value, dict.getValueFromId(actualId));
        }
    }

    @Test
    public void emptyValueTest() {
        ArrayList<String> str = new ArrayList<String>();
        str.add("");
        str.add("part");
        str.add("par");
        str.add("partition");
        str.add("party");
        str.add("parties");
        str.add("paint");
        testStringDictionary(str, null);
    }

    @Test
    public void simpleTrieTest() {
        ArrayList<String> str = new ArrayList<String>();
        str.add("part");
        str.add("part"); // meant to be dup
        str.add("par");
        str.add("partition");
        str.add("party");
        str.add("parties");
        str.add("paint");

        ArrayList<String> notFound = new ArrayList<String>();
        notFound.add("");
        notFound.add("p");
        notFound.add("pa");
        notFound.add("pb");
        notFound.add("parti");
        notFound.add("partz");
        notFound.add("partyz");

        testStringDictionary(str, notFound);
    }

    @Test
    public void dictionaryContainTest() {
        ArrayList<String> str = new ArrayList<String>();
        str.add("part");
        str.add("part"); // meant to be dup
        str.add("par");
        str.add("partition");
        str.add("party");
        str.add("parties");
        str.add("paint");

        TrieDictionaryBuilder<String> b = newDictBuilder(str);
        int baseId = new Random().nextInt(100);
        TrieDictionary<String> dict = b.build(baseId);

        str.add("py");
        b = newDictBuilder(str);
        baseId = new Random().nextInt(100);
        TrieDictionary<String> dict2 = b.build(baseId);

        assertEquals(true, dict2.contains(dict));
        assertEquals(false, dict.contains(dict2));
    }

    @Test
    public void englishWordsTest() throws Exception {
        InputStream is = new FileInputStream("src/test/resources/dict/english-words.80 (scowl-2015.05.18).txt");
        ArrayList<String> str = loadStrings(is);
        testStringDictionary(str, null);
    }

    @Test
    public void categoryNamesTest() throws Exception {
        InputStream is = new FileInputStream("src/test/resources/dict/dw_category_grouping_names.dat");
        ArrayList<String> str = loadStrings(is);
        testStringDictionary(str, null);
    }

    private static void benchmarkStringDictionary(Iterable<String> str) throws IOException {
        TrieDictionaryBuilder<String> b = newDictBuilder(str);
        b.stats().print();
        TrieDictionary<String> dict = b.build(0);

        TreeSet<String> set = new TreeSet<String>();
        for (String s : str) {
            set.add(s);
        }

        // prepare id==>value array and value==>id map
        HashMap<String, Integer> map = new HashMap<String, Integer>();
        String[] strArray = new String[set.size()];
        byte[][] array = new byte[set.size()][];
        Iterator<String> it = set.iterator();
        for (int id = 0; it.hasNext(); id++) {
            String value = it.next();
            map.put(value, id);
            strArray[id] = value;
            array[id] = value.getBytes("UTF-8");
        }

        // System.out.println("Dict size in bytes:  " +
        // MemoryUtil.deepMemoryUsageOf(dict));
        // System.out.println("Map size in bytes:   " +
        // MemoryUtil.deepMemoryUsageOf(map));
        // System.out.println("Array size in bytes: " +
        // MemoryUtil.deepMemoryUsageOf(strArray));

        // warm-up, said that code only got JIT after run 1k-10k times,
        // following jvm options may help
        // -XX:CompileThreshold=1500
        // -XX:+PrintCompilation
        System.out.println("Benchmark awaitig...");
        benchmark("Warm up", dict, set, map, strArray, array);
        benchmark("Benchmark", dict, set, map, strArray, array);
    }

    private static int benchmark(String msg, TrieDictionary<String> dict, TreeSet<String> set, HashMap<String, Integer> map, String[] strArray, byte[][] array) {
        int n = set.size();
        int times = Math.max(10 * 1000 * 1000 / n, 1); // run 10 million lookups
        int keep = 0; // make sure JIT don't OPT OUT function calls under test
        byte[] valueBytes = new byte[dict.getSizeOfValue()];
        long start;

        // benchmark value==>id, via HashMap
        System.out.println(msg + " HashMap lookup value==>id");
        start = System.currentTimeMillis();
        for (int i = 0; i < times; i++) {
            for (int j = 0; j < n; j++) {
                keep |= map.get(strArray[j]);
            }
        }
        long timeValueToIdByMap = System.currentTimeMillis() - start;
        System.out.println(timeValueToIdByMap);

        // benchmark value==>id, via Dict
        System.out.println(msg + " Dictionary lookup value==>id");
        start = System.currentTimeMillis();
        for (int i = 0; i < times; i++) {
            for (int j = 0; j < n; j++) {
                keep |= dict.getIdFromValueBytesWithoutCache(array[j], 0, array[j].length, 0);
            }
        }
        long timeValueToIdByDict = System.currentTimeMillis() - start;
        System.out.println(timeValueToIdByDict);

        // benchmark id==>value, via Array
        System.out.println(msg + " Array lookup id==>value");
        start = System.currentTimeMillis();
        for (int i = 0; i < times; i++) {
            for (int j = 0; j < n; j++) {
                keep |= strArray[j].length();
            }
        }
        long timeIdToValueByArray = System.currentTimeMillis() - start;
        System.out.println(timeIdToValueByArray);

        // benchmark id==>value, via Dict
        System.out.println(msg + " Dictionary lookup id==>value");
        start = System.currentTimeMillis();
        for (int i = 0; i < times; i++) {
            for (int j = 0; j < n; j++) {
                keep |= dict.getValueBytesFromIdWithoutCache(j).length;
            }
        }
        long timeIdToValueByDict = System.currentTimeMillis() - start;
        System.out.println(timeIdToValueByDict);

        return keep;
    }

    private static void testStringDictionary(ArrayList<String> str, ArrayList<String> notFound) {
        TrieDictionaryBuilder<String> b = newDictBuilder(str);
        int baseId = new Random().nextInt(100);
        TrieDictionary<String> dict = b.build(baseId);

        TreeSet<String> set = new TreeSet<String>();
        for (String s : str) {
            set.add(s);
        }

        // test serialize
        dict = testSerialize(dict);

        // test basic id<==>value
        Iterator<String> it = set.iterator();
        int id = baseId;
        for (; it.hasNext(); id++) {
            String value = it.next();
            // System.out.println("checking " + id + " <==> " + value);

            assertEquals(id, dict.getIdFromValue(value));
            assertEquals(value, dict.getValueFromId(id));
        }
        if (notFound != null) {
            for (String s : notFound) {
                try {
                    dict.getIdFromValue(s);
                    fail("For not found value '" + s + "', IllegalArgumentException is expected");
                } catch (IllegalArgumentException e) {
                    // good
                }
            }
        }

        // test null value
        int nullId = dict.getIdFromValue(null);
        assertNull(dict.getValueFromId(nullId));
    }

    private static TrieDictionary<String> testSerialize(TrieDictionary<String> dict) {
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream dataout = new DataOutputStream(bout);
            dict.write(dataout);
            dataout.close();
            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            DataInputStream datain = new DataInputStream(bin);
            TrieDictionary<String> r = new TrieDictionary<String>();
            r.readFields(datain);
            datain.close();
            return r;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static TrieDictionaryBuilder<String> newDictBuilder(Iterable<String> str) {
        TrieDictionaryBuilder<String> b = new TrieDictionaryBuilder<String>(new StringBytesConverter());
        for (String s : str)
            b.addValue(s);
        return b;
    }

    private static ArrayList<String> loadStrings(InputStream is) throws Exception {
        ArrayList<String> r = new ArrayList<String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        try {
            String word;
            while ((word = reader.readLine()) != null) {
                word = word.trim();
                if (word.isEmpty() == false)
                    r.add(word);
            }
        } finally {
            reader.close();
            is.close();
        }
        return r;
    }

    @Test
    public void testSuperLongStringValue() {
        String longPrefix = "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789" + "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789";

        TrieDictionaryBuilder<String> b = new TrieDictionaryBuilder<String>(new StringBytesConverter());
        String v1 = longPrefix + "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz";
        String v2 = longPrefix + "xyz";

        b.addValue(v1);
        b.addValue(v2);
        TrieDictionary<String> dict = b.build(0);
        dict.dump(System.out);
    }

    @Test
    public void testRounding() {
        // see NumberDictionaryTest.testRounding();
    }
}
