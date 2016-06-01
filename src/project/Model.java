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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import coreNLP.Stemmer;
import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.morph.WordnetStemmer;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.trees.TypedDependency;

public abstract class Model {
	protected static final Collection<String> KEPT_RELS =
			Collections.unmodifiableCollection(new HashSet<>(Arrays.asList("nominal subject", "adverbial modifier", "direct object", "adjectival modifier", "compound modifier", "nominal modifier")));
	protected static final ExecutorService pool = Executors.newWorkStealingPool();
	private static final Pattern keySplitter = Pattern.compile("((.+) :: (.+)) ~ ((.+) :: (.+))");
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
	private boolean converted, smoothed;
	
	public Model(boolean requireSynchronized) {
		this.requireSynchronized = requireSynchronized;
		probs = this.requireSynchronized ? Collections.synchronizedMap(new HashMap<>()) : new HashMap<>();
		counts = null;
		converted = false;
		smoothed = false;
	}
	
	public Model(Path root, boolean requireSynchronized) throws IOException {
		this(requireSynchronized);
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
				else if (name.equals("STORED_STATE")) {
					Files.lines(file).forEach(Model.this::parseStateLine);
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
	
	protected void parseStateLine(String line) {
		String[] parts = line.split(" = ");
		switch (parts[0].trim()) {
			case "converted":
				converted = Boolean.parseBoolean(parts[1].trim());
				return;
			case "smoothed":
				smoothed = Boolean.parseBoolean(parts[1].trim());
				return;
		}
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
	
	protected void smooth() {
		if (smoothed)
			return;
		synchronized (probs) {
			if (smoothed)
				return;
			doSmoothing();
			smoothed = true;
		}
	}
	
	protected abstract void doSmoothing();
	
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
	
	public boolean containsBigram(TypedDependency td) {
		if (!shouldRecord(td))
			return false;
		stem(td);
		String name = td.reln().getLongName();
		if (!probs.containsKey(name))
			return false;
		return probs.get(name).containsKey(generateKey(td));
	}
	
	public Double probabilityForBigram(TypedDependency td) {
		convertToProbs();
		if (!shouldRecord(td))
			return 0.0;
		stem(td);
		String name = td.reln().getLongName();
		if (!probs.containsKey(name))
			return 0.0;
		String gov = generateKey(td.gov()), dep = generateKey(td.dep()), key = gov + " ~ " + dep;
		return probs.get(name).get(key);
	}
	
	public Integer countForBigram(TypedDependency td) {
		if (!shouldRecord(td))
			return 0;
		stem(td);
		String name = td.reln().getLongName();
		if (!probs.containsKey(name))
			return 0;
		String gov = generateKey(td.gov()), dep = generateKey(td.dep()), key = gov + " ~ " + dep;
		return probs.get(name).containsKey(key) ? (int) (converted ? probs.get(name).get(key) * counts.get(name) : probs.get(name).get(key)) : 0;
	}
	
	public String generateKey(TypedDependency td) {
		return generateKey(td.gov()) + " ~ " + generateKey(td.dep());
	}
	
	public String generateKey(IndexedWord iw) {
		return generateKey(iw.word(), iw.tag());
	}
	
	public String generateKey(String word, String tag) {
		return word.toLowerCase() + " :: " + tag.substring(0, 2);
	}
	
	/**
	 * Splits the given {@code key} into its 4 parts: gov, gov_pos, dep, and dep_pos.<br>
	 * In the generated {@link MatchResult}, group 1 corresponds to the gov key, groups 2 and 3 correspond to gov and
	 * gov_pos, 4 corresponds to the dep key, and groups 5 and 6 correspond to dep and dep_pos.
	 * 
	 * @param key
	 *            the key to split
	 * @return a {@link MatchResult} wherein group 1 corresponds to the gov key, groups 2 and 3 correspond to gov and
	 *         gov_pos, 4 corresponds to the dep key, and groups 5 and 6 correspond to dep and dep_pos
	 */
	public MatchResult splitKey(String key) {
		Matcher m = keySplitter.matcher(key);
		m.find();
		return m.toMatchResult();
	}
	
	protected void storeState(BufferedWriter bw) throws IOException {
		bw.write("converted = " + converted);
		bw.newLine();
		bw.write("smoothed = " + smoothed);
		bw.newLine();
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
		}active = root.resolve("STORED_STATE");
		Files.deleteIfExists(active);
		Files.createFile(active);
		try (FileWriter fw = new FileWriter(active.toFile()); BufferedWriter bw = new BufferedWriter(fw)) {
			storeState(bw);
		}
	}
}
