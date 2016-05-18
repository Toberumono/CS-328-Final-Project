package project;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import toberumono.lexer.errors.LexerException;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.nndep.DependencyParser;
import edu.stanford.nlp.parser.nndep.demo.DependencyParserDemo;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.logging.Redwood;

public class CorpusParser {
	private static final Collection<String> KEPT_RELS = Collections.unmodifiableCollection(Arrays.asList("nominal subject", "adverbial modifier", "direct object", "adjectival modifier"));
	
	/** A logger for this class */
	private static Redwood.RedwoodChannels log = Redwood.channels(DependencyParserDemo.class);
	
	public static void main(String[] args) throws IOException {
		String modelPath = DependencyParser.DEFAULT_MODEL;
		String taggerPath = "edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-distsim.tagger";
		
		for (int argIndex = 0; argIndex < args.length - 2;) {
			switch (args[argIndex]) {
				case "-tagger":
					taggerPath = args[argIndex + 1];
					argIndex += 2;
					break;
				case "-model":
					modelPath = args[argIndex + 1];
					argIndex += 2;
					break;
				default:
					throw new RuntimeException("Unknown argument " + args[argIndex]);
			}
		}
		
		String text = "I can almost always tell when movies use fake dinosaurs.";//BNCScraper.scrapeBNCFile(Paths.get("/Users/joshualipstone/Downloads/2554/2554/download/Texts/A/A0/A00.xml"));
		
		MaxentTagger tagger = new MaxentTagger(taggerPath);
		DependencyParser parser = DependencyParser.loadFromModelFile(modelPath);
		
		Map<String, Map<String, Integer>> counts = new HashMap<>();
		Map<String, Map<String, Integer>> word_totals = new HashMap<>();
		Map<String, Integer> totals = new HashMap<>();
		for (String name : KEPT_RELS) {
			counts.put(name, new HashMap<>());
			word_totals.put(name + "_g", new HashMap<>());
			word_totals.put(name + "_d", new HashMap<>());
			totals.put(name, 0);
		}
		
		SimpleFileVisitor<Path> corpusVisitor = new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				if (!file.getFileName().toString().endsWith("xml"))
					return FileVisitResult.CONTINUE;
				System.out.println(file);
				try {
					String[] keys = new String[3];
					DocumentPreprocessor tokenizer = new DocumentPreprocessor(new StringReader(BNCScraper.scrapeBNCFile(file)));
					for (List<HasWord> sentence : tokenizer) {
						List<TaggedWord> tagged = tagger.tagSentence(sentence);
						GrammaticalStructure gs = parser.predict(tagged);
						for (TypedDependency td : gs.allTypedDependencies()) {
							//TODO implement stemming
							String name = td.reln().getLongName();
							if (!KEPT_RELS.contains(name))
								continue;
							Model.generateKeys(td, keys);
							
							if (!counts.get(name).containsKey(keys[2])) //Increment count for pair
								counts.get(name).put(keys[2], 0);
							counts.get(name).put(keys[2], counts.get(name).get(keys[2]) + 1);
							if (!word_totals.get(name + "_g").containsKey(keys[0])) //Increment count for gov
								word_totals.get(name + "_g").put(keys[0], 0);
							word_totals.get(name + "_g").put(keys[0], word_totals.get(name + "_g").get(keys[0]) + 1);
							if (!word_totals.get(name + "_d").containsKey(keys[1])) //Increment count for dep
								word_totals.get(name + "_d").put(keys[1], 0);
							word_totals.get(name + "_d").put(keys[1], word_totals.get(name + "_d").get(keys[1]) + 1);
							totals.put(name, totals.get(name) + 1); //Increment total seen
							/*
							System.out.println("REL: " + td.reln().getLongName());
							System.out.println("GOV: " + td.gov().word() + " :: " + td.gov().tag());
							System.out.println("DEP: " + td.dep().word() + " :: " + td.dep().tag());
							System.out.println("---------------------------------");
							*/
						}
						// Print typed dependencies
						//log.info(gs);
					}
				}
				catch (LexerException e) {
					System.err.println(file + " FAILED");
				}
				return FileVisitResult.CONTINUE;
			}
		};
		Files.walkFileTree(Paths.get(args[args.length - 2]), corpusVisitor);
		Path model = Paths.get(args[args.length - 1]);
		Files.createDirectories(model);
		for (String name : KEPT_RELS) {
			double count = totals.get(name).doubleValue();
			Path active = model.resolve(name);
			Files.deleteIfExists(active);
			Files.createFile(active);
			try (FileWriter fw = new FileWriter(active.toFile()); BufferedWriter bw = new BufferedWriter(fw)) {
				counts.get(name).entrySet().stream().map(e -> e.getKey() + " = " + (e.getValue().doubleValue() / count)).forEach(s -> {
					try {
						bw.write(s);
						bw.newLine();
					}
					catch (IOException e) {
						e.printStackTrace();
					}
				});
			}
			active = model.resolve(name + "_g");
			Files.deleteIfExists(active);
			Files.createFile(active);
			try (FileWriter fw = new FileWriter(active.toFile()); BufferedWriter bw = new BufferedWriter(fw)) {
				word_totals.get(name + "_g").entrySet().stream().map(e -> e.getKey() + " = " + (e.getValue().doubleValue() / count)).forEach(s -> {
					try {
						bw.write(s);
						bw.newLine();
					}
					catch (IOException e) {
						e.printStackTrace();
					}
				});
			}
			active = model.resolve(name + "_d");
			Files.deleteIfExists(active);
			Files.createFile(active);
			try (FileWriter fw = new FileWriter(active.toFile()); BufferedWriter bw = new BufferedWriter(fw)) {
				word_totals.get(name + "_d").entrySet().stream().map(e -> e.getKey() + " = " + (e.getValue().doubleValue() / count)).forEach(s -> {
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
