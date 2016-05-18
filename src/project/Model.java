package project;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

import edu.stanford.nlp.trees.TypedDependency;

public class Model {
	private final Map<String, Map<String, Double>> probs;
	
	public Model(Path root) throws IOException {
		probs = new HashMap<>();
		SimpleFileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				System.out.println(file);
				String name = file.getFileName().toString();
				probs.put(name, new HashMap<>());
				Files.lines(file).forEach(line -> {
					int equals = line.lastIndexOf('=');
					probs.get(name).put(line.substring(0, equals).trim(), Double.parseDouble(line.substring(0, equals + 1).trim()));
				});
				return FileVisitResult.CONTINUE;
			}
		};
		Files.walkFileTree(root, visitor);
	}
	
	public static String[] generateKeys(TypedDependency td) {
		return generateKeys(td, new String[3]);
	}
	
	public static String[] generateKeys(TypedDependency td, String[] container) {
		String gov = td.gov().word() + " :: " + td.gov().tag(), dep = td.dep().word() + " :: " + td.dep().tag();
		String key = gov + " ~ " + dep;
		container[0] = gov;
		container[1] = dep;
		container[2] = key;
		return container;
	}
}
