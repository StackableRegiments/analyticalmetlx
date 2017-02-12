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

    private final String _inputHtmlDir;
    private final String _outputHtmlDir;
    private final String _outputJsSubDir;
    private final String _minifiedHtmlExtension;

    public Minifier(final String inputHtmlDir,
                    final String outputHtmlDir,
                    final String outputJsSubDir,
                    final String minifiedHtmlExtension) {
        _inputHtmlDir = inputHtmlDir;
        _outputHtmlDir = outputHtmlDir;
        _outputJsSubDir = outputJsSubDir;
        _minifiedHtmlExtension = minifiedHtmlExtension;
    }

    public void minify(final List<String> args) throws IOException {
        long startTime = Calendar.getInstance().getTimeInMillis();
        for (String arg : args) {
            doMinify(arg);
        }

        final long duration = (Calendar.getInstance().getTimeInMillis() - startTime) / 1000;
        System.out.println(String.format("Minifier finished in %ds.", duration));
    }

    private void doMinify(final String inputHtmlName) throws IOException {
        final Path inputHtmlPath = Paths.get(_inputHtmlDir + File.separator + inputHtmlName + ".html").toAbsolutePath();
        System.out.println("Minifying js from: " + inputHtmlPath);

        // Compile javascript to single file.
        final Path outputJsDir = Paths.get(_outputHtmlDir + File.separator + _outputJsSubDir);
        if (!Files.exists(outputJsDir)) {
            Files.createDirectories(outputJsDir);
        }

        final String jsToMinifyRegex = ".*static/js/.*\\.js.*";

        final Path outputJsPath = Paths.get(outputJsDir + File.separator + inputHtmlName + ".js");
        final String compiled = compile(grep(jsToMinifyRegex, inputHtmlPath)
                .map(s -> s.replaceFirst(".*src=\"", ""))
                .map(s -> s.replaceFirst("\".*", ""))
                .map(s -> new File(_inputHtmlDir + File.separator + s)));
        try (BufferedWriter writer = Files.newBufferedWriter(outputJsPath)) {
            writer.write(compiled);
        }

        // Copy original html and replace javascript references with single file reference.
        final Path outputHtmlDir = Paths.get(_outputHtmlDir);
        if (!Files.exists(outputHtmlDir)) {
            Files.createDirectories(outputHtmlDir);
        }
        final Path outputHtmlPath = Paths.get(outputHtmlDir + File.separator + inputHtmlName + _minifiedHtmlExtension);
        try (Stream<String> stream = Files.lines(inputHtmlPath)) {
            try (BufferedWriter writer = Files.newBufferedWriter(outputHtmlPath)) {
                stream.filter(line -> !line.matches(jsToMinifyRegex))
                        .map(line -> line.matches(".*<span class=\"minifiedScript\"></span>.*") ?
                                "<script data-lift=\"with-resource-id\" src=\"" +
                                        _outputJsSubDir + "/" + inputHtmlName + ".js\"></script>" :
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
    }

    /**
     * @param inputFiles Files containing JavaScript source code to compile.
     * @return The compiled version of the code.
     */
    private String compile(final Stream<File> inputFiles) {
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
