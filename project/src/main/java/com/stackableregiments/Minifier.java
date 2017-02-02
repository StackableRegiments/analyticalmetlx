package com.stackableregiments;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.WarningLevel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Stream;

public class Minifier {

    private static final String INPUT_HTML_DIR = "src/main/webapp/";
    private static final String OUTPUT_HTML_DIR = "target/extra-resources/";
    private static final String OUTPUT_JS_DIR = "target/extra-resources/minified/";
    private static final String MINIFIED_HTML_EXTENSION = ".hmin";

    public static void minify(List<String> args) throws IOException {
        long startTime = Calendar.getInstance().getTimeInMillis();
        System.out.println("Minifier started...");

        for (String arg : args) {
            doMinify(arg);
        }
//        for (final String inputHtmlName : args) {
/*
        args.forEach(a -> {
            try {
                doMinify(a);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
*/

        final long duration = (Calendar.getInstance().getTimeInMillis() - startTime) / 1000;
        System.out.println(String.format("Minifier finished in %ds.", duration));
    }

    private static void doMinify(final String inputHtmlName) throws IOException {
        final Path inputHtmlPath = Paths.get(INPUT_HTML_DIR + inputHtmlName + ".html").toAbsolutePath();
        System.out.println("Minifying js from: " + inputHtmlPath);

        // Compile javascript to single file.
        final Path outputJsDir = Paths.get(OUTPUT_JS_DIR);
        if (!Files.exists(outputJsDir)) {
            Files.createDirectories(outputJsDir);
        }
        final Path outputJsPath = Paths.get(outputJsDir + File.separator + inputHtmlName + ".js");
        final String compiled = compile(grep(".*static/js.*\\.js.*", inputHtmlPath)
                .map(s -> s.replaceFirst(".*src=\"", ""))
                .map(s -> s.replaceFirst("\".*", ""))
                .map(s -> new File(INPUT_HTML_DIR + s)));
        try (BufferedWriter writer = Files.newBufferedWriter(outputJsPath)) {
            writer.write(compiled);
        }
        System.out.println("Created minified js in: " + outputJsPath.toString());

        // Copy original html and replace javascript references with single file reference.
        final Path outputHtmlDir = Paths.get(OUTPUT_HTML_DIR);
        if (!Files.exists(outputHtmlDir)) {
            Files.createDirectories(outputHtmlDir);
        }
        final Path outputHtmlPath = Paths.get(outputHtmlDir + File.separator + inputHtmlName + MINIFIED_HTML_EXTENSION);
        try (Stream<String> stream = Files.lines(inputHtmlPath)) {
            try (BufferedWriter writer = Files.newBufferedWriter(outputHtmlPath)) {
                stream.filter(line -> !line.matches(".*static/js.*\\.js.*"))
                        .map(line -> line.matches(".*<span class=\"minifiedScript\"></span>.*") ?
                                "<script data-lift=\"with-resource-id\" src=\"minified/board.js\"></script>" :
                                line)
                        .forEach(line -> {
                            try {
                                writer.write(line + "\n");
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            }
        }

        System.out.println("Created modified html in: " + outputHtmlPath.toString());
    }

    /**
     * @param inputFiles Files containing JavaScript source code to compile.
     * @return The compiled version of the code.
     */
    private static String compile(final Stream<File> inputFiles) {
        // To get the complete set of externs, the logic in
        // CompilerRunner.getDefaultExterns() should be used here.
//        final SourceFile extern = SourceFile.fromCode("externs.js", "function alert(x) {}");
        final List externs = new ArrayList<SourceFile>();

        final List inputs = new ArrayList<SourceFile>();
        inputFiles.forEach(f -> inputs.add(SourceFile.fromFile(f)));

        final CompilerOptions options = new CompilerOptions();
        // Simple mode is used here, but additional options could be set, too.
        CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
        WarningLevel.QUIET.setOptionsForWarningLevel(options);

        // compile() returns a Result, but it is not needed here.
        final Compiler compiler = new Compiler();
        compiler.compile(externs, inputs, options);

        System.out.println(String.format("Compiled %d js files into 1 file.", inputs.size()));

        // The compiler is responsible for generating the compiled code; it is not
        // accessible via the Result.
        return compiler.toSource();
    }

    private static Stream<String> grep(final String pattern, final Path path) throws IOException {
        return Files.lines(path).filter(line -> line.matches(pattern));
    }
}
