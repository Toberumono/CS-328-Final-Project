package project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import toberumono.lexer.BasicDescender;
import toberumono.lexer.BasicLexer;
import toberumono.lexer.BasicRule;
import toberumono.lexer.errors.LexerException;
import toberumono.lexer.util.DefaultIgnorePatterns;
import toberumono.structures.sexpressions.BasicConsType;
import toberumono.structures.sexpressions.ConsCell;
import toberumono.structures.sexpressions.ConsType;

/**
 * This helper class is used to cache the logic used to scrape files from the BNC.
 * 
 * @author Joshua Lipstone
 */
public class BNCScraper {
	private static BasicLexer lexer = null;
	private static final ConsType text = new BasicConsType("text");
	private static final ConsType sentence = new BasicConsType("sentence");
	
	public static synchronized final BasicLexer getBNCScraper() { //This initializes the rules needed to scrape files from the BNC
		if (lexer == null) {
			lexer = new BasicLexer(DefaultIgnorePatterns.WHITESPACE);
			lexer.addDescender("Sentence", new BasicDescender(Pattern.compile("<s( .*?)?((?<!/)>)"), Pattern.compile("</s>", Pattern.LITERAL), (l, s, m) -> {
				String sen = "";
				if (m != null)
					for (ConsCell word : m)
						sen += (String) word.getCar();
				return new ConsCell(sen, sentence);
			}));
			lexer.addRule("Word", new BasicRule(Pattern.compile("<w( [^>]*?)?((?<!/)>)(.+?)</w>"), (l, s, m) -> {
				if (m.group(3).contains(">")) {
					System.out.println(m.group());
					return new ConsCell();
				}
				return new ConsCell(m.group(3), text);
			}));
			lexer.addRule("Punc", new BasicRule(Pattern.compile("<c( [^>]*?)?((?<!/)>)(.+?)</c>"), (l, s, m) -> {
				if (m.group(3).equals("\u2026"))
					return new ConsCell(", ", text);
				return new ConsCell(m.group(3), text);
			}));
			lexer.addIgnore("Other Stuff", Pattern.compile("<([^wsc/][^>]*?|[^>]{2,}?|/[^wsc][^>]*?|/[^>]{2,}?)>"));
		}
		return lexer;
	}
	
	public static String scrapeBNCFile(Path file) throws IOException {
		final StringBuilder sb = new StringBuilder();
		try (Stream<String> lines = Files.lines(file)) {
			lines.forEach(l -> sb.append(l).append("\n"));
		}
		return scrapeBNCText(sb.toString());
	}
	
	public static String scrapeBNCText(String text) {
		text = text.replaceAll("<teiHeader>.*?</teiHeader>", ""); //Remove the header information because it is a pain.
		StringBuilder output = new StringBuilder(text.length() / 3);
		ConsCell out;
		try {
			out = getBNCScraper().lex(text);
		}
		catch (LexerException e) {
			System.err.println("Error Parsing BNC Text");
			out = (ConsCell) e.getState().getRoot();
		}
		if (out == null)
			return "";
		for (ConsCell cell : out)
			if (cell.getCarType() == sentence)
				output.append((String) cell.getCar()).append("\n");
		return output.toString();
	}
	
	/**
	 * Simple test method
	 * 
	 * @param args
	 *            command-line arguments (ignored)
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public static void main(String[] args) throws IOException {
		System.out.println(scrapeBNCFile(Paths.get("/Users/joshualipstone/Downloads/2554/2554/download/Texts/A/A0/A00.xml")));
	}
}
