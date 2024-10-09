package org.opengroup.osdu.storage;

import static org.junit.platform.console.ConsoleLauncher.run;

import io.qameta.allure.ConfigurationBuilder;
import io.qameta.allure.Extension;
import io.qameta.allure.ReportGenerator;
import io.qameta.allure.allure1.Allure1Plugin;
import io.qameta.allure.allure2.Allure2Plugin;
import io.qameta.allure.category.CategoriesPlugin;
import io.qameta.allure.category.CategoriesTrendPlugin;
import io.qameta.allure.context.FreemarkerContext;
import io.qameta.allure.context.JacksonContext;
import io.qameta.allure.context.MarkdownContext;
import io.qameta.allure.context.RandomUidContext;
import io.qameta.allure.core.AttachmentsPlugin;
import io.qameta.allure.core.Configuration;
import io.qameta.allure.core.MarkdownDescriptionsPlugin;
import io.qameta.allure.core.Plugin;
import io.qameta.allure.core.TestsResultsPlugin;
import io.qameta.allure.duration.DurationPlugin;
import io.qameta.allure.duration.DurationTrendPlugin;
import io.qameta.allure.environment.Allure1EnvironmentPlugin;
import io.qameta.allure.executor.ExecutorPlugin;
import io.qameta.allure.history.HistoryPlugin;
import io.qameta.allure.history.HistoryTrendPlugin;
import io.qameta.allure.idea.IdeaLinksPlugin;
import io.qameta.allure.influxdb.InfluxDbExportPlugin;
import io.qameta.allure.launch.LaunchPlugin;
import io.qameta.allure.mail.MailPlugin;
import io.qameta.allure.owner.OwnerPlugin;
import io.qameta.allure.plugin.DefaultPluginLoader;
import io.qameta.allure.prometheus.PrometheusExportPlugin;
import io.qameta.allure.retry.RetryPlugin;
import io.qameta.allure.retry.RetryTrendPlugin;
import io.qameta.allure.severity.SeverityPlugin;
import io.qameta.allure.status.StatusChartPlugin;
import io.qameta.allure.suites.SuitesPlugin;
import io.qameta.allure.summary.SummaryPlugin;
import io.qameta.allure.tags.TagsPlugin;
import io.qameta.allure.timeline.TimelinePlugin;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.platform.console.options.CommandResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestRunner {

  private static final String ALLURE_RAW_REPORTS = getProvidedPropertyOrDefault("ALLURE_RAW_REPORTS", "./target/allure-results");
  private static final String ALLURE_OUTPUT_DIR = getProvidedPropertyOrDefault("ALLURE_OUTPUT_DIR", "./results");
  private static final String ALLURE_PLUGINS = getProvidedPropertyOrDefault("ALLURE_PLUGINS", "./.allure/allure-2.26.0/plugins");

  private static String getProvidedPropertyOrDefault(String key, String defaultVal) {
    String provided = System.getProperty(key, System.getenv(key));
    return (provided == null || provided.isEmpty()) ? defaultVal : provided;
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(TestRunner.class);

  private static final List<Extension> EXTENSIONS = Arrays.asList(
      new JacksonContext(),
      new MarkdownContext(),
      new FreemarkerContext(),
      new RandomUidContext(),
      new MarkdownDescriptionsPlugin(),
      new TagsPlugin(),
      new RetryPlugin(),
      new RetryTrendPlugin(),
      new SeverityPlugin(),
      new OwnerPlugin(),
      new IdeaLinksPlugin(),
      new HistoryPlugin(),
      new HistoryTrendPlugin(),
      new CategoriesPlugin(),
      new CategoriesTrendPlugin(),
      new DurationPlugin(),
      new DurationTrendPlugin(),
      new StatusChartPlugin(),
      new TimelinePlugin(),
      new SuitesPlugin(),
      new TestsResultsPlugin(),
      new AttachmentsPlugin(),
      new MailPlugin(),
      new InfluxDbExportPlugin(),
      new PrometheusExportPlugin(),
      new SummaryPlugin(),
      new ExecutorPlugin(),
      new LaunchPlugin(),
      new Allure1Plugin(),
      new Allure1EnvironmentPlugin(),
      new Allure2Plugin()
  );


  public static void main(String[] args) throws IOException {
    String[] junitArgs = {
        "--select-package", "org.opengroup.osdu.storage"
    };

    // Programmatically run JUnit tests
    PrintWriter out = new PrintWriter(System.out);
    PrintWriter err = new PrintWriter(System.err);
    CommandResult<?> result = run(out, err, junitArgs);


    String[] dirArgs = {
        ALLURE_RAW_REPORTS,
        ALLURE_OUTPUT_DIR
    };

    Files.createDirectories(Paths.get(ALLURE_OUTPUT_DIR));

    final int lastIndex = dirArgs.length- 1;
    final Path[] files = getFiles(dirArgs);
    final List<Plugin> plugins = loadPlugins();
    LOGGER.info("Found {} plugins", plugins.size());
    plugins.forEach(plugin -> LOGGER.info(plugin.getConfig().getName()));
    final Configuration configuration = new ConfigurationBuilder()
        .withExtensions(EXTENSIONS)
        .withPlugins(plugins)
        .build();
    final ReportGenerator generator = new ReportGenerator(configuration);
    generator.generate(files[lastIndex], Arrays.asList(Arrays.copyOf(files, lastIndex)));

    System.exit(result.getExitCode());
  }

  public static Path[] getFiles(final String... paths) {
    return Arrays.stream(paths)
        .map(Paths::get)
        .toArray(Path[]::new);
  }

  public static List<Plugin> loadPlugins() throws IOException {
    final Optional<Path> optional = Optional.of(Paths.get(ALLURE_PLUGINS))
        .filter(Files::isDirectory);
    if (!optional.isPresent()) {
      return Collections.emptyList();
    }
    final Path pluginsDirectory = optional.get();
    LOGGER.info("Found plugins directory {}", pluginsDirectory);
    final DefaultPluginLoader loader = new DefaultPluginLoader();
    final ClassLoader classLoader = TestRunner.class.getClassLoader();
    return Files.list(pluginsDirectory)
        .filter(Files::isDirectory)
        .map(pluginDir -> loader.loadPlugin(classLoader, pluginDir))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }
}
