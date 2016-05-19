package project;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
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
import java.util.Map;
import java.util.Map.Entry;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.trees.TypedDependency;
import coreNLP.Stemmer;

public abstract class Model {
	private static final Collection<String> KEPT_RELS =
			Collections.unmodifiableCollection(new HashSet<>(Arrays.asList("nominal subject", "adverbial modifier", "direct object", "adjectival modifier")));
	
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
		SimpleFileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				System.out.println(file);
				String name = file.getFileName().toString();
				probs.put(name, new HashMap<>());
				Files.lines(file).forEach(line -> {
					int equals = line.indexOf('=');
					probs.get(name).put(line.substring(0, equals).trim(), Double.parseDouble(line.substring(0, equals + 1).trim()));
				});
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
			converted = true;
			for (Entry<String, Map<String, Double>> bigramType : probs.entrySet()) {
				double count = counts.get(bigramType.getKey().indexOf('_') > -1 ? bigramType.getKey().substring(0, bigramType.getKey().indexOf('_')) : bigramType.getKey());
				for (Entry<String, Double> e : bigramType.getValue().entrySet())
					e.setValue(e.getValue() / count);
			}
			smooth();
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
		return td.gov() != null && td.dep() != null && KEPT_RELS.contains(td.reln().getLongName());
	}
	
	public void stem(TypedDependency td) {
		stem(td.gov());
		stem(td.dep());
		return;
	}
	
	public void stem(IndexedWord iw) {
		if (!shouldStem(iw))
			return;
		iw.setTag(iw.tag().substring(0, 2));
		iw.setWord(new Stemmer().stem(iw.word()));
		return;
	}
	
	protected boolean shouldStem(IndexedWord iw) {
		return iw.tag().length() > 2 && iw.tag().startsWith("VB"); //If it is a non-base-form verb
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
	
	protected String generateKey(IndexedWord iw) {
		return iw.word() + " :: " + iw.tag();
	}
	
	public void storeModel(Path root) throws IOException {
		convertToProbs();
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
	}
}
