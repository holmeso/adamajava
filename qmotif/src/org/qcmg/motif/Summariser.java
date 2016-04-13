package org.qcmg.motif;


import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.qcmg.common.log.QLogger;
import org.qcmg.common.log.QLoggerFactory;
import org.qcmg.common.string.StringUtils;
import org.qcmg.common.util.LoadReferencedClasses;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class Summariser {
	
	private static QLogger logger = null;
	private static final int exitStatus = 1;
	
	private String inputFile;
	private String logFile;
	private String version;
	
	
	private int letsGo() throws IOException {
		/*
		 * get the qmotif xml files to be summarised
		 */
		List<String>inputs = loadInputsFromFile(inputFile);
		logger.info("Will attempt to summarise " + inputs.size() + " qmotif xml files");
		if (inputs.isEmpty()) {
			logger.warn("No qmotif xml files found in " + inputFile);
			return exitStatus;
		}
		
		/*
		 * get summary data
		 */
		
		
		
		return 0;
	}
	
	public static String getSummaryData(String file) throws ParserConfigurationException, SAXException, IOException {
		if ( ! StringUtils.isNullOrEmpty(file)) {
			/*
			 * get pertinent bits of info from qmotif xml file
			 */
			try (Stream<String> lines = Files.lines(Paths.get(file), Charset.defaultCharset());) {
				String x = lines.filter(s -> s.startsWith("<summary") 
						|| s.startsWith("<totalReadCount")
						|| s.startsWith("<noOfMotifs")
						|| s.startsWith("<rawUnmapped")
						|| s.startsWith("<rawIncludes")
						|| s.startsWith("<rawGenomic")
						|| s.startsWith("<scaledUnmapped")
						|| s.startsWith("<scaledIncludes")
						|| s.startsWith("<scaledGenomic")
						).collect(Collectors.joining("\t"));
				x = x.replace("<", "");
				x = x.replace("/>", "");
				x = x.replace(">", "");
				x = x.replace("\"", "");
				x = x.replace("count=", "");
				x = x.replace("summary", "");
				return x;
			}
		}
		return null;
	}
	
	public static List<String> getAllSummaryData(List<String> inputFiles) {
		
		inputFiles.forEach(f -> {
			
		});
		
		return Collections.emptyList();
	}
	
	public static List<String> loadInputsFromFile(String inputFile) throws IOException {
		if (null != inputFile) {
			try (Stream<String> lines = Files.lines(Paths.get(inputFile), Charset.defaultCharset());) {
				return lines.map(s -> s.toString()).collect(Collectors.toList());
			}
		}
		return Collections.emptyList();
	}
	
	
	public static void main(final String[] args) throws Exception {
		LoadReferencedClasses.loadClasses(Motif.class);
		Summariser operation = new Summariser();
		int exitStatus = operation.setup( args );
		if (null != logger)
			logger.logFinalExecutionStats(exitStatus);
		else
			System.err.println(Messages.USAGE);
		System.exit(exitStatus);
		
	}
	
	int setup(String [] args) throws Exception{
		Options options = new Options(args);
		if (options.hasHelpOption() || null == args || args.length == 0) {
//			System.out.println(Messages.USAGE);
			options.displayHelp();
			return exitStatus;
		} else if (options.hasVersionOption()) {
			System.err.println(Messages.getVersionMessage());
			return exitStatus;
		} else {
			options.detectBadOptions();
			// loop through supplied files - check they can be read
			// configure logging
			logFile = options.getLog();
			version = Summariser.class.getPackage().getImplementationVersion();
			if (null == version) {
				version = "test";
			}
			logger = QLoggerFactory.getLogger(Summariser.class, logFile, options.getLogLevel());
			// get input file
			inputFile = options.getInput();
		}
		return letsGo();
	}
}
