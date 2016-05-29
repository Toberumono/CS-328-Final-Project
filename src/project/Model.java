package project;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import coreNLP.Stemmer;
import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.morph.WordnetStemmer;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.trees.TypedDependency;

public abstract class Model {
	protected static final Collection<String> KEPT_RELS =
			Collections.unmodifiableCollection(new HashSet<>(Arrays.asList("nominal subject", "adverbial modifier", "direct object", "adjectival modifier", "compound modifier", "nominal modifier")));
	private static final WordnetStemmer stemmer;
	static {
		WordnetStemmer wns = null;
		try {
			URL url = new URL("file", null, System.getProperty("wordnet.database.dir"));
			IDictionary dict = new Dictionary(url);
			dict.open();
			wns = new WordnetStemmer(dict);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		stemmer = wns;
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			if (stemmer != null)
				stemmer.getDictionary().close();
		}));
	}
	
	private final boolean requireSynchronized;
	protected final Map<String, Map<String, Double>> probs;
	protected Map<String, Integer> counts;
	private boolean converted;
	
	public Model(boolean requireSynchronized) {
		this.requireSynchronized = requireSynchronized;
		probs = this.requireSynchronized ? Collections.synchronizedMap(new HashMap<>()) : new HashMap<>();
		counts = null;
		converted = false;
	}
	
	public Model(Path root, boolean requireSynchronized) throws IOException {
		this(requireSynchronized);
		converted = true;
		SimpleFileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				System.out.println(file);
				String name = file.getFileName().toString();
				if (name.equals("STORED_COUNTS")) {
					initCounts();
					Files.lines(file).forEach(line -> {
						int equals = line.lastIndexOf('=');
						counts.put(line.substring(0, equals).trim(), Integer.parseInt(line.substring(equals + 1).trim()));
					});
				}
				else {
					probs.put(name, new HashMap<>());
					Files.lines(file).forEach(line -> {
						int equals = line.lastIndexOf('=');
						probs.get(name).put(line.substring(0, equals).trim(), Double.parseDouble(line.substring(equals + 1).trim()));
					});
				}
				return FileVisitResult.CONTINUE;
			}
		};
		Files.walkFileTree(root, visitor);
	}
	
	public void convertToProbs() {
		if (converted) //Outside of synchronized block to avoid that step if possible
			return;
		synchronized (probs) {
			if (converted) //Have to re-check here
				return;
			for (Entry<String, Map<String, Double>> bigramType : probs.entrySet()) {
				double count = counts.get(bigramType.getKey().indexOf('_') > -1 ? bigramType.getKey().substring(0, bigramType.getKey().indexOf('_')) : bigramType.getKey());
				for (Entry<String, Double> e : bigramType.getValue().entrySet())
					e.setValue(e.getValue() / count);
			}
			converted = true;
		}
	}
	
	protected abstract void smooth();
	
	private void initCounts() {
		if (counts == null) {
			synchronized (probs) {
				if (counts == null)
					counts = requireSynchronized ? Collections.synchronizedMap(new HashMap<>()) : new HashMap<>();
			}
		}
	}
	
	protected boolean shouldRecord(TypedDependency td) {
		return td.reln().getLongName() != null && td.gov().tag() != null && td.dep().tag() != null &&
				(KEPT_RELS.contains(td.reln().getLongName()) || (td.gov().tag().startsWith("NN") && td.dep().tag().startsWith("NN"))) &&
				td.gov().tag().length() > 1 && td.dep().tag().length() > 1;
	}
	
	public void stem(TypedDependency td) {
		stem(td.gov());
		stem(td.dep());
		return;
	}
	
	public void stem(IndexedWord iw) {
		if (iw.tag().startsWith("NNP")) {
			iw.setWord("{PROPER NOUN}");
			iw.setTag("NNP");
			return;
		}
		if (!shouldStem(iw))
			return;
		iw.setTag(iw.tag().substring(0, iw.tag().length() - 1));
		List<String> stems = stemmer.findStems(iw.word(), JWIIntegration.posAdapter(iw.tag()));
		iw.setWord(stems.size() > 0 ? stems.get(0) : new Stemmer().stem(iw.word()));
		return;
	}
	
	protected boolean shouldStem(IndexedWord iw) {
		return iw.tag().length() > 2 && (iw.tag().startsWith("VB") || iw.tag().startsWith("NN")); //If it is a non-base-form verb
	}
	
	public void addBigram(TypedDependency td) {
		/*
		System.out.println("REL: " + td.reln().getLongName());
		System.out.println("GOV: " + td.gov().word() + " :: " + td.gov().tag());
		System.out.println("DEP: " + td.dep().word() + " :: " + td.dep().tag());
		System.out.println("---------------------------------");
		*/
		if (!shouldRecord(td))
			return;
		initCounts();
		stem(td);
		String name = td.reln().getLongName(), gov = generateKey(td.gov()), dep = generateKey(td.dep()), key = gov + " ~ " + dep;
		if (!probs.containsKey(name)) {
			synchronized (probs) {
				if (!probs.containsKey(name)) { //Have to re-check inside the synchronized block
					probs.put(name, new HashMap<>());
					probs.put(name + "_g", new HashMap<>());
					probs.put(name + "_d", new HashMap<>());
					counts.put(name, 0);
				}
			}
		}
		synchronized (probs.get(name)) {
			probs.get(name).put(key, probs.get(name).containsKey(key) ? probs.get(name).get(key) + 1.0 : 1.0); //Increment count for pair
			probs.get(name + "_g").put(gov, probs.get(name + "_g").containsKey(gov) ? probs.get(name + "_g").get(gov) + 1.0 : 1.0); //Increment count for gov
			probs.get(name + "_d").put(dep, probs.get(name + "_d").containsKey(dep) ? probs.get(name + "_d").get(dep) + 1.0 : 1.0); //Increment count for dep
			counts.put(name, counts.get(name) + 1); //Increment total seen
		}
	}
	
	public Double getHighestProbabilityForBigram(String first, String firstPos, String second, String secondPos) {
		convertToProbs();
		double highest = 0.0;
		String key = generateKey(first, firstPos) + " ~ " + generateKey(second, secondPos);
		for (String mapping : counts.keySet())
			if (probs.get(mapping).containsKey(key) && probs.get(mapping).get(key) > highest)
				highest = probs.get(mapping).get(key);
		key = generateKey(second, secondPos) + " ~ " + generateKey(first, firstPos);
		for (String mapping : counts.keySet())
			if (probs.get(mapping).containsKey(key) && probs.get(mapping).get(key) > highest)
				highest = probs.get(mapping).get(key);
		return highest;
	}
	
	public Double probabilityForBigram(TypedDependency td) {
		convertToProbs();
		stem(td);
		String name = td.reln().getLongName();
		if (!probs.containsKey(name))
			return 0.0;
		String gov = generateKey(td.gov()), dep = generateKey(td.dep()), key = gov + " ~ " + dep;
		return probs.get(name).get(key);
	}
	
	public Integer countForBigram(TypedDependency td) {
		stem(td);
		String name = td.reln().getLongName();
		if (!probs.containsKey(name))
			return 0;
		String gov = generateKey(td.gov()), dep = generateKey(td.dep()), key = gov + " ~ " + dep;
		return probs.get(name).containsKey(key) ? (int) (converted ? probs.get(name).get(key) * counts.get(name) : probs.get(name).get(key)) : 0;
	}
	
	public String generateKey(IndexedWord iw) {
		return generateKey(iw.word(), iw.tag());
	}
	
	public String generateKey(String word, String tag) {
		return word.toLowerCase() + " :: " + tag.substring(0, 2);
	}
	
	public void storeModel(Path root) throws IOException {
		convertToProbs();
		smooth();
		Files.createDirectories(root);
		for (Entry<String, Map<String, Double>> entry : probs.entrySet()) {
			Path active = root.resolve(entry.getKey());
			Files.deleteIfExists(active);
			Files.createFile(active);
			try (FileWriter fw = new FileWriter(active.toFile()); BufferedWriter bw = new BufferedWriter(fw)) {
				entry.getValue().entrySet().stream().map(e -> e.getKey() + " = " + e.getValue()).forEach(s -> {
					try {
						bw.write(s);
						bw.newLine();
					}
					catch (IOException e) {
						e.printStackTrace();
					}
				});
			}
		}
		Path active = root.resolve("STORED_COUNTS");
		Files.deleteIfExists(active);
		Files.createFile(active);
		try (FileWriter fw = new FileWriter(active.toFile()); BufferedWriter bw = new BufferedWriter(fw)) {
			counts.entrySet().stream().map(e -> e.getKey() + " = " + e.getValue()).forEach(s -> {
				try {
					bw.write(s);
					bw.newLine();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			});
		}
	}
}
