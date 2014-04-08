/**
 * © Copyright The University of Queensland 2010-2014.  This code is released under the terms outlined in the included LICENSE file.
 */
/**
 * All source code distributed as part of the AdamaJava project is released
 * under the GNU GENERAL PUBLIC LICENSE Version 3, a copy of which is
 * included in this distribution as gplv3.txt.
 */
package org.qcmg.qprofiler.bam;

import java.io.File;

import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMRecord;

import org.qcmg.common.date.DateUtils;
import org.qcmg.common.log.QLevel;
import org.qcmg.common.log.QLogger;
import org.qcmg.common.log.QLoggerFactory;
import org.qcmg.picard.SAMFileReaderFactory;
import org.qcmg.qprofiler.report.SummaryReport;
import org.qcmg.qprofiler.summarise.Summarizer;

public class BamSummarizer implements Summarizer {
	
	private String [] includes;
	private String [] tags;
	private String [] tagsInt;
	private String [] tagsChar;
	private int maxRecords;
	private String validation;
	
	private final static QLogger logger = QLoggerFactory.getLogger(BamSummarizer.class);
	
	public BamSummarizer() {}	// default constructor
	
	public BamSummarizer(String [] includes, int maxRecords, String [] tags, String [] tagsInt, String [] tagsChar, String validation) {
		this.includes = includes;
		this.maxRecords = maxRecords;
		this.tags = tags;
		this.tagsInt = tagsInt;
		this.tagsChar = tagsChar;
		this.validation = validation;
	}
	
	@Override
	public SummaryReport summarize(File file) throws Exception {
		SAMFileReader reader = SAMFileReaderFactory.createSAMFileReader(file, validation);
		
		// create the SummaryReport
		BamSummaryReport bamSummaryReport = new BamSummaryReport(includes, maxRecords, tags, tagsInt, tagsChar);
		bamSummaryReport.setFileName(file.getAbsolutePath());
		bamSummaryReport.setStartTime(DateUtils.getCurrentDateAsString());
		
		boolean logLevelEnabled = logger.isLevelEnabled(QLevel.DEBUG);
		
		// iterate over the SAMRecord objects returned, passing them to the summariser
		long currentRecordCount = 0;
		try {
			for (SAMRecord samRecord : reader) {
				try {
					bamSummaryReport.parseRecord(samRecord);
				} catch (Exception e) {
					logger.error("Error caught parsing SAMRecord with readName: " + samRecord.getReadName(), e);
					throw e;
				}
				
				currentRecordCount = bamSummaryReport.getRecordsParsed();
				
				if (logLevelEnabled && currentRecordCount % FEEDBACK_LINES_COUNT == 0) {
					logger.debug("Records parsed: " + currentRecordCount);
				}
				
				// if maxRecords is non-zero, stop when we hit it
				if (currentRecordCount == maxRecords) {
	//				if (maxRecords > 0 && currentRecordCount == maxRecords) {
					break;
				}
			}
			bamSummaryReport.setBamHeader(reader.getFileHeader().getTextHeader());
			
		} finally {
			reader.close();
		}
		
		bamSummaryReport.cleanUp();
		logger.info("Records parsed: "+ bamSummaryReport.getRecordsParsed());
		bamSummaryReport.setFinishTime(DateUtils.getCurrentDateAsString());
		return bamSummaryReport;
	}
}