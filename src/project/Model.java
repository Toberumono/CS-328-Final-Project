package project;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.DirectoryStream;
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

/**
 * The base class for the models used in our project.
 * 
 * @author Joshua Lipstone
 */
public abstract class Model {
	protected static final Collection<String> KEPT_RELS =
			Collections.unmodifiableCollection(new HashSet<>(Arrays.asList("nominal subject", "adverbial modifier", "direct object", "adjectival modifier", "compound modifier", "nominal modifier")));
	protected static final ExecutorService pool = Executors.newWorkStealingPool();
	private static final Pattern keySplitter = Pattern.compile("((.+) :: (.+)) ~ ((.+) :: (.+))");
	private static final WordnetStemmer stemmer;
	static { //This block is automatically run the first time the class is initialized the JVM.  This means that it is guarenteed to run exactly once.
		WordnetStemmer wns = null;
		try {
			URL url = new URL("file", null, System.getProperty("wordnet.database.dir"));
			IDictionary dict = new Dictionary(url);
			dict.open();
			wns = new WordnetStemmer(dict);
		}
		catch (IOException e) {
			System.err.println("Please set the wordnet.database.dir system property. If you installed WordNet 3.1 through Homebrew, include" +
					"\n'-Dwordnet.database.dir=\"/usr/local/Cellar/wordnet/3.1/dict\"' in your launch command.");
			throw new RuntimeException();
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
	
	/**
	 * Constructs an empty {@link Model}
	 * 
	 * @param requireSynchronized
	 *            if {@code true}, the constructed {@link Model} should use thread-safe operations only
	 */
	public Model(boolean requireSynchronized) {
		this.requireSynchronized = requireSynchronized;
		probs = this.requireSynchronized ? Collections.synchronizedMap(new HashMap<>()) : new HashMap<>();
		counts = null;
		converted = false;
		smoothed = false;
	}
	
	/**
	 * Constructs a {@link Model} using the data found in {@code root}.
	 * 
	 * @param root
	 *            the {@link Path} to the root directory of the {@link Model Model's} data
	 * @param requireSynchronized
	 *            if {@code true}, the constructed {@link Model} should use thread-safe operations only
	 * @throws IOException
	 *             if an I/O error occurs while loading the stored data
	 */
	public Model(Path root, boolean requireSynchronized) throws IOException {
		this(requireSynchronized);
		SimpleFileVisitor<Path> visitor = new SimpleFileVisitor<Path>() { //This allows files in the model to be organized instead of all being in the root directory
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				System.err.println("Loading: " + file);
				String name = file.getFileName().toString();
				if (name.equals("STORED_COUNTS")) { //The counts for every map are stored in a separate file for ease of parsing
					initCounts();
					Files.lines(file).forEach(line -> {
						int equals = line.lastIndexOf('=');
						counts.put(line.substring(0, equals).trim(), Integer.parseInt(line.substring(equals + 1).trim()));
					});
				}
				else if (name.equals("STORED_STATE")) { //The state of the model is stored in a file for extensibility purpose (by default just whether it's values have been converted to probabilities and whether those probabilities have been smoothed)
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
	
	/**
	 * Converts the counts in the {@link Model} to probabilities by dividing the number of times each relationship has been
	 * seen by the total number of times that relationships of that type have been seen.<br>
	 * <b>Note:</b> this method internally tracks whether it has already been run on the {@link Model}. Therefore, it is safe
	 * to call it multiple times.
	 */
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
	
	/**
	 * Smoothes the probabilities in the {@link Model}.<br>
	 * <b>Note:</b> <i>This method assumes that {@link #convertToProbs()} has already been called.</i><br>
	 * <b>Note:</b> This method internally tracks whether it has already been run on the {@link Model}. Therefore, it is safe
	 * to call it multiple times.
	 */
	public void smooth() {
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
	
	/**
	 * Stems the passed {@link TypedDependency} (specifically it's {@link TypedDependency#gov() gov} and
	 * {@link TypedDependency#dep() dep} fields) <i>in place</i> using the algorithm specified by the {@link Model}.<br>
	 * <b>Note:</b> This may modify the tag as well as the word.
	 * 
	 * @param td
	 *            the {@link TypedDependency} to stem
	 */
	public void stem(TypedDependency td) {
		stem(td.gov());
		stem(td.dep());
		return;
	}
	
	/**
	 * Stems the passed {@link IndexedWord} <i>in place</i> using the algorithm specified by the {@link Model}.<br>
	 * <b>Note:</b> This may modify the tag as well as the word.
	 * 
	 * @param iw
	 *            the {@link IndexedWord} to stem
	 */
	public void stem(IndexedWord iw) {
		if (iw.tag().startsWith("NNP")) { //We do this with all proper nouns in order to have sufficient data for how they can be used.
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
	
	/**
	 * Determines whether the given {@link IndexedWord} should be stemmed.
	 * 
	 * @param iw
	 *            the {@link IndexedWord} to stem
	 * @return {@code true} iff {@code iw} should be stemmed
	 */
	protected boolean shouldStem(IndexedWord iw) {
		return iw.tag().length() > 2 && (iw.tag().startsWith("VB") || iw.tag().startsWith("NN")); //If it is a non-base-form verb or noun
	}
	
	/**
	 * Adds the bigram specified by the {@link TypedDependency} to the {@link Model}.
	 * 
	 * @param td
	 *            the {@link TypedDependency} to add to the {@link Model}
	 */
	public void addBigram(TypedDependency td) {
		if (!shouldRecord(td))
			return;
		initCounts();
		stem(td);
		String name = td.reln().getLongName(), gov = generateKey(td.gov()), dep = generateKey(td.dep()), key = gov + " ~ " + dep;
		if (!probs.containsKey(name)) {
			synchronized (probs) { //If we haven't added a relation of this type before, we need to initialize its maps
				if (!probs.containsKey(name)) { //Have to re-check inside the synchronized block
					probs.put(name, new HashMap<>());
					probs.put(name + "_g", new HashMap<>());
					probs.put(name + "_d", new HashMap<>());
					counts.put(name, 0);
				}
			}
		}
		synchronized (probs.get(name)) { //At this point, we know that that map exists, so we can synchronize on it, thereby allowing different types relations to be added in parallel, thereby reducing the bottleneck 
			probs.get(name).put(key, probs.get(name).containsKey(key) ? probs.get(name).get(key) + 1.0 : 1.0); //Increment count for pair
			probs.get(name + "_g").put(gov, probs.get(name + "_g").containsKey(gov) ? probs.get(name + "_g").get(gov) + 1.0 : 1.0); //Increment count for gov
			probs.get(name + "_d").put(dep, probs.get(name + "_d").containsKey(dep) ? probs.get(name + "_d").get(dep) + 1.0 : 1.0); //Increment count for dep
			counts.put(name, counts.get(name) + 1); //Increment total seen
		}
	}
	
	/**
	 * Determines the highest probability across all relation types for the given bigrams that can be generated from the
	 * given words and their PoS tags.
	 * 
	 * @param first
	 *            the first word
	 * @param firstPos
	 *            the first word's PoS tag
	 * @param second
	 *            the second word
	 * @param secondPos
	 *            the second word's PoS tag
	 * @return the highest probability across all relation types for the given bigrams that can be generated from the given
	 *         words and their PoS tags
	 */
	public Double getHighestProbabilityForBigram(String first, String firstPos, String second, String secondPos) {
		convertToProbs();
		smooth();
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
	
	/**
	 * Determines whether the {@link Model} contains the bigram specified by the given {@link TypedDependency}.<br>
	 * <b>Note:</b> This method <i>will</i> modify the {@link TypedDependency} in-place.
	 * 
	 * @param td
	 *            the {@link TypedDependency} for which to check
	 * @return {@code true} iff the {@link Model} contains the bigram specified by the {@link TypedDependency}
	 */
	public boolean containsBigram(TypedDependency td) {
		if (!shouldRecord(td) || !probs.containsKey(td.reln().getLongName()))
			return false;
		stem(td);
		return probs.get(td.reln().getLongName()).containsKey(generateKey(td));
	}
	
	/**
	 * Determines the probability of the bigram specified by the given {@link TypedDependency}.<br>
	 * <b>Note:</b> This method <i>will</i> modify the {@link TypedDependency} in-place.
	 * 
	 * @param td
	 *            the {@link TypedDependency} that specifies the bigram for which to get the probability
	 * @return the probability of the bigram specified by the given {@link TypedDependency}
	 */
	public Double probabilityForBigram(TypedDependency td) {
		convertToProbs();
		smooth();
		if (!shouldRecord(td))
			return 0.0;
		stem(td);
		String name = td.reln().getLongName();
		if (!probs.containsKey(name))
			return 0.0;
		String gov = generateKey(td.gov()), dep = generateKey(td.dep()), key = gov + " ~ " + dep;
		return probs.get(name).get(key);
	}
	
	/**
	 * Determines the total number of times that the bigram specified by the given {@link TypedDependency} has been seen.
	 * Given that this is supposed to determine the raw counts, it's behavior is undefined on a smoothed model.<br>
	 * <b>Note:</b> This method <i>will</i> modify the {@link TypedDependency} in-place.
	 * 
	 * @param td
	 *            the {@link TypedDependency} that specifies the bigram
	 * @return the number of times of the bigram specified by the given {@link TypedDependency}
	 */
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
	
	/**
	 * @param td
	 *            the {@link TypedDependency} for which the key should be generated
	 * @return the key used to access data about the bigram specified by the {@link TypedDependency}
	 */
	public String generateKey(TypedDependency td) {
		return generateKey(td.gov()) + " ~ " + generateKey(td.dep());
	}
	
	/**
	 * @param iw
	 *            the {@link IndexedWord} for which the key should be generated
	 * @return the key used to access data about the word/pos-tag pair specified by the {@link IndexedWord}
	 */
	public String generateKey(IndexedWord iw) {
		return generateKey(iw.word(), iw.tag());
	}
	
	/**
	 * @param word
	 *            the word for which to generate the key
	 * @param tag
	 *            the PoS tag of the word
	 * @return the key used to access data about the given word/tag pair
	 */
	public String generateKey(String word, String tag) {
		return word.toLowerCase() + " :: " + tag.substring(0, 2);
	}
	
	/**
	 * Splits the given {@code key} into its 4 parts: gov, gov_pos, dep, and dep_pos.<br>
	 * In the generated {@link MatchResult}, group 1 corresponds to the gov key, groups 2 and 3 correspond to gov and
	 * gov_pos, 4 corresponds to the dep key, and groups 5 and 6 correspond to dep and dep_pos.
	 * 
	 * @param key
	 *            the key of the form, "gov :: gov_pos ~ dep :: dep_pos", to split
	 * @return a {@link MatchResult} wherein group 1 corresponds to the gov key, groups 2 and 3 correspond to gov and
	 *         gov_pos, 4 corresponds to the dep key, and groups 5 and 6 correspond to dep and dep_pos
	 */
	public MatchResult splitKey(String key) {
		Matcher m = keySplitter.matcher(key);
		m.find();
		return m.toMatchResult();
	}
	
	/**
	 * Writes the state information of the {@link Model} (by default just whether it's values have been converted to
	 * probabilities and whether those probabilities have been smoothed) to the given {@link BufferedWriter}
	 * 
	 * @param bw
	 *            the {@link BufferedWriter} to which the state information should be written
	 * @throws IOException
	 *             if an I/O error occurs while writing to the {@link BufferedWriter}
	 */
	protected void storeState(BufferedWriter bw) throws IOException {
		bw.write("converted = " + converted);
		bw.newLine();
		bw.write("smoothed = " + smoothed);
		bw.newLine();
	}
	
	/**
	 * Writes the {@link Model Model's} data and state information to the directory specified by {@code root}.
	 * 
	 * @param root
	 *            the root directory into which the {@link Model Model's} data and state information
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public void storeModel(Path root) throws IOException {
		convertToProbs();
		smooth();
		if (Files.exists(root)) //We wipe the old Model's files to prevent possible mixing of data
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
				for (Path p : stream)
					try {
						Files.deleteIfExists(p);
					}
					catch (IOException e) {
						e.printStackTrace();
					}
			}
		else
			Files.createDirectories(root);
		
		//Write every map to disk
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
		//Write the counts to disk
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
		//Write the state to disk
		active = root.resolve("STORED_STATE");
		Files.deleteIfExists(active);
		Files.createFile(active);
		try (FileWriter fw = new FileWriter(active.toFile()); BufferedWriter bw = new BufferedWriter(fw)) {
			storeState(bw);
		}
	}
}
