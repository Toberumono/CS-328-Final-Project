package project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import toberumono.lexer.BasicDescender;
import toberumono.lexer.BasicLexer;
import toberumono.lexer.BasicRule;
import toberumono.lexer.util.DefaultIgnorePatterns;
import toberumono.structures.sexpressions.BasicConsType;
import toberumono.structures.sexpressions.ConsCell;
import toberumono.structures.sexpressions.ConsType;

public class BNCScraper {
	private static BasicLexer lexer = null;
	private static final ConsType text = new BasicConsType("text");
	private static final ConsType sentence = new BasicConsType("sentence");
	
	public static synchronized final BasicLexer getBNCScraper() {
		if (lexer == null) {
			lexer = new BasicLexer(DefaultIgnorePatterns.WHITESPACE);
			lexer.addDescender("Sentence", new BasicDescender(Pattern.compile("<s.*?>"), Pattern.compile("</s>", Pattern.LITERAL), (l, s, m) -> {
				String sen = "";
				for (ConsCell word : m)
					sen += (String) word.getCar();
				return new ConsCell(sen, sentence);
			}));
			lexer.addRule("Word", new BasicRule(Pattern.compile("<w.*?>(.*?)</w>"), (l, s, m) -> new ConsCell(m.group(1), text)));
			lexer.addRule("Punc", new BasicRule(Pattern.compile("<c.*?>(.*?)</c>"), (l, s, m) -> {
				if (m.group(1).equals("\u2026"))
					return new ConsCell(", ", text);
				return new ConsCell(m.group(1), text);
			}));
			lexer.addIgnore("Other Stuff", Pattern.compile("<([^wsc/][^>]*?|[^>]{2,}?|/[^wsc][^>]*?|/[^>]{2,}?)>"));
		}
		return lexer;
	}
	
	public static String scrapeBNCFile(Path file) throws IOException {
		final StringBuilder sb = new StringBuilder();
		Files.lines(file).forEach(l -> sb.append(l).append("\n"));
		return scrapeBNCText(sb.toString());
	}
	
	public static String scrapeBNCText(String text) {
		text = text.replaceAll("<teiHeader>.*?</teiHeader>", "");
		ConsCell out = getBNCScraper().lex(text);
		StringBuilder output = new StringBuilder(text.length() / 3);
		for (ConsCell cell : out)
			output.append((String) cell.getCar()).append("\n");
		return output.toString();
	}
	
	public static void main(String[] args) throws IOException {
		System.out.println(scrapeBNCFile(Paths.get("/Users/joshualipstone/Downloads/2554/2554/download/Texts/A/A0/A00.xml")));
	}
}
