package docCompare;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigSyntax;


import docCompare.Exclusions;

import docCompare.env.Environment;

public class Exclusions {

	
	

	    private static final Logger LOG = LoggerFactory.getLogger(Exclusions.class);
	    private final Environment environment;
	    private final float CM_TO_PIXEL;
	    private final float MM_TO_PIXEL;
	    private final float PT_TO_PIXEL;
	    private static final Pattern NUMBER = Pattern.compile("([0-9.]+)(cm|mm|pt)");
	    private static final ConfigParseOptions configParseOptions = ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF).setAllowMissing(true);
	    private final Map<Integer, PageExclusions> exclusionsPerPage = new HashMap<>();
	    private final PageExclusions exclusionsForAllPages = new PageExclusions();

	    
	    
	    public Exclusions(Environment environment) {
	        this.environment = environment;
	        int dpi = environment.getDPI();
	        CM_TO_PIXEL = 1f / 2.54f * dpi;
	        MM_TO_PIXEL = CM_TO_PIXEL / 10f;
	        PT_TO_PIXEL = ((float) dpi) / 72f;
	    }

	    
	    
	    public Exclusions add(final PageArea exclusion) {
	        Objects.requireNonNull(exclusion);
	        if (exclusion.hasPage()) {
	            exclusionsPerPage.computeIfAbsent(exclusion.page, k -> new PageExclusions(exclusionsForAllPages)).add(exclusion);
	        } else {
	            exclusionsForAllPages.add(exclusion);
	        }
	        return this;
	    }

	    public Exclusions remove(final PageArea exclusion) {
	        Objects.requireNonNull(exclusion);
	        if (exclusion.hasPage()) {
	            exclusionsPerPage.computeIfAbsent(exclusion.page, k -> new PageExclusions(exclusionsForAllPages)).remove(exclusion);
	        } else {
	            exclusionsForAllPages.remove(exclusion);
	        }
	        return this;
	    } 
	    
	    public PageExclusions forPage(final int page) {
	        return exclusionsPerPage.getOrDefault(page, exclusionsForAllPages);
	    }

	    public void readExclusions(final String filename) {
	        Objects.requireNonNull(filename, "filename must not be null");
	        readExclusions(new File(filename));
	    }

	    public void readExclusions(final Path path) {
	        Objects.requireNonNull(path, "path must not be null");
	        readExclusions(path.toFile());
	    }
	    
	    public void readExclusions(final File file) {
	        Objects.requireNonNull(file, "file must not be null");
	        if (file.exists()) {
	            final Config exclusionConfig = ConfigFactory.parseFile(file, configParseOptions);
	            readFromConfig(exclusionConfig);
	        } else {
	            if (environment.failOnMissingIgnoreFile()) {
	                throw new IgnoreFileMissing(file);
	            }
	            LOG.info("Ignore-file at '{}' not found. Continuing without ignores.", file);
	        }
	    }
	    
	    
	    public void readExclusions(InputStream inputStream) {
	        Objects.requireNonNull(inputStream, "inputStream must not be null");
	        try (final InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
	            readExclusions(inputStreamReader);
	        } catch (IOException e) {
	            LOG.warn("Could not read ignores from InputStream. Continuing without ignores.", e);
	        }
	    }

	    public void readExclusions(Reader reader) {
	        Objects.requireNonNull(reader, "reader must not be null");
	        final Config exclusionConfig = ConfigFactory.parseReader(reader, configParseOptions);
	        readFromConfig(exclusionConfig);
	    }

	    private void readFromConfig(final Config exclusionConfig) {
	        final List<? extends ConfigObject> exclusions = exclusionConfig.getObjectList("exclusions");
	        exclusions.stream().map(co -> {
	            final Config c = co.toConfig();
	            if (!c.hasPath("x1") && !c.hasPath("y1") && !c.hasPath("x2") && !c.hasPath("y2")) {
	                return new PageArea(c.getInt("page"));
	            }
	            if (c.hasPath("page")) {
	                return new PageArea(c.getInt("page"), toPix(c, "x1"), toPix(c, "y1"), toPix(c, "x2"), toPix(c, "y2"));
	            }
	            return new PageArea(toPix(c, "x1"), toPix(c, "y1"), toPix(c, "x2"), toPix(c, "y2"));
	        }).forEach(this::add);
	    }

	    private int toPix(final Config c, final String key) {
	        try {
	            return c.getInt(key);
	        } catch (ConfigException.WrongType e) {
	            final String valueStr = c.getString(key);
	            final Matcher matcher = NUMBER.matcher(valueStr);
	            if (matcher.matches()) {
	                float factor = 0;
	                if ("mm".equals(matcher.group(2))) {
	                    factor = MM_TO_PIXEL;
	                } else if ("cm".equals(matcher.group(2))) {
	                    factor = CM_TO_PIXEL;
	                } else if ("pt".equals(matcher.group(2))) {
	                    factor = PT_TO_PIXEL;
	                }
	                return Math.round(factor * Float.parseFloat(matcher.group(1)));
	            } else {
	                throw new RuntimeException("Exclusion can't be read. String not parseable to a number: " + valueStr);
	            }
	        }
	    }

	    public void forEach(final Consumer<PageArea> exclusionConsumer) {
	        getPageAreaStream().forEach(exclusionConsumer);
	    }

	    public String asJson() {
	        return PageArea.asJsonWithExclusion(getPageAreaStream());
	    }

	    private Stream<PageArea> getPageAreaStream() {
	        Stream<PageArea> allPages = exclusionsForAllPages.getExclusions().stream();
	        Stream<PageArea> pageAreas = exclusionsPerPage.entrySet().stream()
	                .sorted(Map.Entry.comparingByKey())
	                .flatMap(e -> e.getValue().getExclusions().stream());
	        return Stream.concat(allPages, pageAreas);
	    }   
	    
	    
	    
	    
	    
	    
	    
	    
	    
	    
	    
	    
	    
}
