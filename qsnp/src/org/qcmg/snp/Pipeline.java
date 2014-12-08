/**
 * © Copyright The University of Queensland 2010-2014.  This code is released under the terms outlined in the included LICENSE file.
 */

package org.qcmg.snp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.sf.picard.reference.FastaSequenceFile;
import net.sf.picard.reference.ReferenceSequence;
import net.sf.samtools.Cigar;
import net.sf.samtools.CigarElement;
import net.sf.samtools.CigarOperator;
import net.sf.samtools.SAMFileHeader;
import net.sf.samtools.SAMRecord;
import net.sf.samtools.SAMSequenceDictionary;
import net.sf.samtools.SAMSequenceRecord;

import org.ini4j.Ini;
import org.qcmg.common.log.QLevel;
import org.qcmg.common.log.QLogger;
import org.qcmg.common.log.QLoggerFactory;
import org.qcmg.common.meta.QBamId;
import org.qcmg.common.meta.QDccMeta;
import org.qcmg.common.meta.QExec;
import org.qcmg.common.model.Accumulator;
import org.qcmg.common.model.ChrPosition;
import org.qcmg.common.model.PileupElement;
import org.qcmg.common.model.PileupElementLite;
import org.qcmg.common.model.Rule;
import org.qcmg.common.string.StringUtils;
import org.qcmg.common.util.ChrPositionUtils;
import org.qcmg.common.util.Constants;
import org.qcmg.common.util.Pair;
import org.qcmg.common.util.PileupElementLiteUtil;
import org.qcmg.common.util.PileupUtils;
import org.qcmg.common.util.SnpUtils;
import org.qcmg.common.util.TabTokenizer;
import org.qcmg.common.vcf.VcfRecord;
import org.qcmg.common.vcf.VcfUtils;
import org.qcmg.common.vcf.header.VcfHeaderUtils;
import org.qcmg.maths.FisherExact;
import org.qcmg.picard.MultiSAMFileIterator;
import org.qcmg.picard.MultiSAMFileReader;
import org.qcmg.picard.SAMFileReaderFactory;
import org.qcmg.picard.SAMRecordFilterWrapper;
import org.qcmg.picard.util.PileupElementUtil;
import org.qcmg.picard.util.QBamIdFactory;
import org.qcmg.picard.util.QDccMetaFactory;
import org.qcmg.picard.util.SAMUtils;
import org.qcmg.pileup.QSnpRecord;
import org.qcmg.pileup.QSnpRecord.Classification;
import org.qcmg.qbamfilter.query.QueryExecutor;
import org.qcmg.snp.util.GenotypeComparisonUtil;
import org.qcmg.snp.util.IniFileUtil;
import org.qcmg.snp.util.RulesUtil;
import org.qcmg.vcf.VCFFileWriter;

public abstract class Pipeline {
	
	static final String ILLUMINA_MOTIF = "GGT";
	
	//TODO these will need to become user defined values at some point
	int novelStartsFilterValue = 4;
	int mutantReadsFilterValue = 5;
	
	static int initialTestSumOfCountsLimit = 3;
	static int baseQualityPercentage = 10;
	
	// STATS FOR CLASSIFIER
	long classifyCount = 0;
	long classifyGermlineCount = 0;
	long classifySomaticCount = 0;
	long classifyGermlineLowCoverage = 0;
	long classifySomaticLowCoverage = 0;
	long classifyNoClassificationCount = 0;
	long classifyNoMutationCount = 0;
	
	long pValueCount = 0;

	/*///////////////////////
	 * COLLECTIONS
	 *///////////////////////
	// map to hold chromosome conversion data
//	final Map<String, String> ensembleToQCMG = new HashMap<>(128);
	
	// ChrPosition chromosome consists of "chr" and then the number/letter
	final Map<ChrPosition,QSnpRecord> positionRecordMap = new HashMap<>(100000);
	
	final Map<ChrPosition,VcfRecord> compoundSnps = new HashMap<>();
	
	final Map<ChrPosition, Pair<Accumulator, Accumulator>> accumulators = new HashMap<>(); 
	
	int[] controlStartPositions;
	int[] testStartPositions;
	int noOfControlFiles;
	int noOfTestFiles;
	boolean includeIndels;
	int mutationId;
	
//	boolean vcfOnlyMode;
	
	List<Rule> controlRules = new ArrayList<>();
	List<Rule> testRules = new ArrayList<>();
	
	String validation;
	String skipAnnotation;
	boolean runSBIASAnnotation = true;
	
	protected final boolean singleSampleMode;
	
	String referenceFile;
	String query;
	
	FastaSequenceFile sequenceFile;
	byte[] referenceBases;
	int referenceBasesLength;
	
	long noOfRecordsFailingFilter = 1000000;
	String currentChr = "chr1";

	////////
	// ids
	///////
	protected String controlSampleId;
	protected String testSampleId;
	protected String mutationIdPrefix;
	protected String patientId;
	
	////////////////////
	// output files
	////////////////////
	protected String vcfFile;
	
	//////////////////
	// input files
	//////////////////
	protected String [] controlBams;
	protected String [] testBams;
	
	protected  QLogger logger;
	protected QExec qexec;

	protected boolean includeDuplicates;
	
	/**
	 * default constructor that sets up a logger for the subclass instance 
	 */
	public Pipeline (QExec qexec, boolean singleSampleMode) {
		logger = QLoggerFactory.getLogger(getClass());
		this.qexec = qexec;
		this.singleSampleMode =singleSampleMode;
	}
	
	/*/////////////////////////////////////
	 * ABSTRACT METHODS
	 *////////////////////////////////////
	
//	protected abstract void ingestIni(Wini ini) throws SnpException;
	protected abstract String getFormattedRecord(final QSnpRecord record, final String ensemblChr);
	protected abstract String getOutputHeader(final boolean isSomatic);
	
	
	/*////////////////////////////////////////
	 * IMPLEMENTED METHODS
	 *////////////////////////////////////////
	
	
	void ingestIni(Ini ini) throws SnpException {
		
		// IDS
		patientId = IniFileUtil.getEntry(ini, "ids", "donor");
		controlSampleId = IniFileUtil.getEntry(ini, "ids", "controlSample");
		testSampleId = IniFileUtil.getEntry(ini, "ids", "testSample");
		
		// INPUT FILES
		referenceFile = IniFileUtil.getInputFile(ini, "ref");
		controlBams = IniFileUtil.getInputFiles(ini, "controlBam");
		testBams = IniFileUtil.getInputFiles(ini, "testBam");
		
		
		// OUTPUT FILES
		vcfFile = IniFileUtil.getOutputFile(ini, "vcf");
		
		// QBAMFILTER QUERY
		query =  IniFileUtil.getEntry(ini, "parameters", "filter");
		if ( ! StringUtils.isNullOrEmpty(IniFileUtil.getEntry(ini, "parameters", "includeDuplicates"))) {
			includeDuplicates =  Boolean.parseBoolean(IniFileUtil.getEntry(ini, "parameters", "includeDuplicates"));
		}
		String sFailFilter = IniFileUtil.getEntry(ini, "parameters", "noOfRecordsFailingFilter");
		if (null != sFailFilter)
			noOfRecordsFailingFilter = Long.parseLong(sFailFilter);
		
		// ADDITIONAL SETUP	
		mutationIdPrefix = qexec.getUuid().getValue() + "_SNP_";
		
//		// VCF ONLY MODE
//		String vcfModeString = IniFileUtil.getEntry(ini, "parameters", "annotateMode"); 
//		vcfOnlyMode = (null == vcfModeString || "vcf".equalsIgnoreCase(vcfModeString));
		
		String novelStartsFilterValueString = IniFileUtil.getEntry(ini, "parameters", "numberNovelStarts");
		// default to 4 if not specified
		if ( ! StringUtils.isNullOrEmpty(novelStartsFilterValueString)) {
			novelStartsFilterValue = Integer.parseInt(novelStartsFilterValueString);
		}
		
		String mutantReadsFilterValueString = IniFileUtil.getEntry(ini, "parameters", "numberMutantReads");
		// default to 5 if not specified
		if ( ! StringUtils.isNullOrEmpty(mutantReadsFilterValueString)) {
			mutantReadsFilterValue = Integer.parseInt(mutantReadsFilterValueString);
		}
		
		// validation
		String validationString = IniFileUtil.getEntry(ini, "parameters", "validation");
		if ( ! StringUtils.isNullOrEmpty(validationString)) {
			validation = validationString;
		}
		
		// validation
		String skipAnnotationString = IniFileUtil.getEntry(ini, "parameters", "skipAnnotation");
		if ( ! StringUtils.isNullOrEmpty(skipAnnotationString)) {
			skipAnnotation = skipAnnotationString;
			if (skipAnnotation.contains(SnpUtils.STRAND_BIAS)) 
				runSBIASAnnotation = false; 
		}
		
		// LOG
		logger.tool("**** IDS ****");
		logger.tool("patient ID: " + patientId);
		logger.tool("analysisId: " + qexec.getUuid().getValue());
		logger.tool("controlSampleId: " + controlSampleId);
		logger.tool("testSampleId: " + testSampleId);
		
		logger.tool("**** INPUT FILES ****");
		if (null != controlBams) {
			logger.tool("controlBam: " + Arrays.deepToString(controlBams));
		}
		if (null != testBams) {
			logger.tool("testBam: " + Arrays.deepToString(testBams));
		}
		if (null != referenceFile) {
			logger.tool("referenceFile: " + referenceFile);
		}
		
		logger.tool("**** OUTPUT FILES ****");
		logger.tool("vcf: " + vcfFile);
		
		logger.tool("**** CONFIG ****");
//		logger.tool("vcfOnlyMode: " + vcfOnlyMode);
		logger.tool("mutantReadsFilterValue: " + mutantReadsFilterValue);
		logger.tool("novelStartsFilterValue: " + novelStartsFilterValue);
		if ( ! StringUtils.isNullOrEmpty(query)) {
			logger.tool("query: " + query);
			logger.tool("noOfRecordsFailingFilter: " + noOfRecordsFailingFilter);
		}
		if ( ! StringUtils.isNullOrEmpty(validation)) {
			logger.tool("validation: " + validation);
		}
		if ( ! StringUtils.isNullOrEmpty(skipAnnotation)) {
			logger.tool("skipAnnotation: " + skipAnnotation);
			logger.tool("runSBIASAnnotation: " + runSBIASAnnotation);
		}
		logger.tool("includeDuplicates: " + includeDuplicates);
	}
	
	String getDccMetaData() throws Exception {
		if (null == controlBams || controlBams.length == 0 || 
				StringUtils.isNullOrEmpty(controlBams[0]) 
				|| null == testBams || testBams.length == 0 
				|| StringUtils.isNullOrEmpty(testBams[0])) return null;
		
		SAMFileHeader controlHeader = SAMFileReaderFactory.createSAMFileReader(controlBams[0]).getFileHeader();
		SAMFileHeader analysisHeader = SAMFileReaderFactory.createSAMFileReader(testBams[0]).getFileHeader();
		
		QDccMeta dccMeta = QDccMetaFactory.getDccMeta(qexec, controlHeader, analysisHeader, "qSNP");
		
		return dccMeta.getDCCMetaDataToString();
	}
	
//	String getLimsMetaData(String type, String bamFileName) throws Exception {
//		SAMFileHeader header = SAMFileReaderFactory.createSAMFileReader(bamFileName).getFileHeader();
//		QLimsMeta limsMeta = QLimsMetaFactory.getLimsMeta(type, bamFileName, header);
//		return limsMeta.getLimsMetaDataToString();
//	}
	
	QBamId [] getBamIdDetails(String [] bamFileName) throws Exception {
		if (null != bamFileName && bamFileName.length > 0) {
			QBamId [] bamIds = new QBamId[bamFileName.length];
			for (int i = 0 ; i < bamFileName.length ; i++) {
				bamIds[i] = QBamIdFactory.getBamId(bamFileName[i]);
			}
			return bamIds;
		} else {
			return null;
		}
	}
	
	String getExistingVCFHeaderDetails() {
		// override this if dealing with input VCFs and the existing headers are to be kept
		return null;
	}
	
	void writeVCF(String outputFileName) throws Exception {
		if (StringUtils.isNullOrEmpty(outputFileName)) {
			logger.warn("No vcf output file scpecified so can't output vcf");
			return;
		}
		logger.info("Writing VCF output");
		
		QBamId[] normalBamIds = getBamIdDetails(controlBams);
		QBamId[] tumourBamIds = getBamIdDetails(testBams);
		
		String header = VcfUtils.getHeaderForQSnp(patientId, controlSampleId, testSampleId, "qSNP v" + Main.version, normalBamIds, tumourBamIds, qexec.getUuid().getValue(), singleSampleMode);
		
		// if 
		String existingHeaders = getExistingVCFHeaderDetails();
		if (null != existingHeaders) {
			header += existingHeaders;
		}
		
		List<ChrPosition> orderedList = new ArrayList<ChrPosition>(positionRecordMap.keySet());
		orderedList.addAll(compoundSnps.keySet());
		Collections.sort(orderedList);
		
		try (VCFFileWriter writer = new VCFFileWriter(new File(outputFileName));) {
			writer.addHeader(header);
			for (ChrPosition position : orderedList) {
				
				QSnpRecord record = positionRecordMap.get(position);
				if (null != record) {
					
				
					// only write output if its been classified
					if (null != record.getClassification()) {
						writer.add(convertQSnpToVCF(record));
					}
				} else {
					// get from copoundSnp map
					VcfRecord vcf = compoundSnps.get(position);
					writer.add(vcf);
					
				}
			}
		}
	}
	
	void classifyPileupRecord(QSnpRecord record) {
		if (null != record.getNormalGenotype() && null != record.getTumourGenotype()) {
			classifyCount++;
			GenotypeComparisonUtil.compareGenotypes(record);
		} else if (singleSampleMode) {
			GenotypeComparisonUtil.singleSampleGenotype(record);
		} else {
			// new code to deal with only 1 genotype being available (due to low/no coverage in normal or tumour)
			// only go in here if we don't have a classification set
			// in vcf mode this can be set when there is low coverage (ie. not enough to provide a genotype)
			// need to make sure that in torrent mode, when we come to re-assessing a position that this doesn't bite us...
			if (null == record.getClassification()) {
				GenotypeComparisonUtil.compareSingleGenotype(record);
			}
			
			if (null == record.getNormalGenotype())  {
				classifyGermlineCount++;
				if (record.getNormalCount() > 0) classifyGermlineLowCoverage++;
			} else  {
				classifySomaticCount++;
				if (record.getTumourCount() > 0) classifySomaticLowCoverage++;
			}
			if (null == record.getClassification()) classifyNoClassificationCount++;
			if (null == record.getMutation()) classifyNoMutationCount++;
		}
		
		if (Classification.SOMATIC == record.getClassification() || Classification.UNKNOWN == record.getClassification()) {
			
			String altString = null != record.getMutation() ? SnpUtils.getAltFromMutationString(record.getMutation()) : null;
//			final char alt = null != record.getMutation() ? SnpUtils.getAltFromMutationString(record.getMutation()) : '\u0000';
			final int mutantReadCount = SnpUtils.getCountFromNucleotideString(record.getTumourNucleotides(), altString);
			if (mutantReadCount < mutantReadsFilterValue) {
				VcfUtils.updateFilter(record.getVcfRecord(), SnpUtils.MUTANT_READS);
			}
			if (record.getNovelStartCount() < novelStartsFilterValue) {
				VcfUtils.updateFilter(record.getVcfRecord(), SnpUtils.NOVEL_STARTS);
			}
		}
		
		// set to PASS if no other filters have been set
		if (StringUtils.isNullOrEmpty(record.getAnnotation()) || "--".equals(record.getAnnotation())) {
			VcfUtils.updateFilter(record.getVcfRecord(), VcfHeaderUtils.FILTER_PASS);
		}
	}
	
	void classifyPileup() {
		
		for (QSnpRecord record : positionRecordMap.values()) {
			classifyPileupRecord(record);
		}
		logger.info("No of records that have a genotype: " + classifyCount + ", out of " + positionRecordMap.size() 
				+ ". Somatic: " + classifySomaticCount + "[" + classifySomaticLowCoverage + "], germline: " 
				+ classifyGermlineCount + "[" + classifyGermlineLowCoverage + "] no classification: " + classifyNoClassificationCount + ", no mutation: " + classifyNoMutationCount);
	}
	
	
	
	private List<SAMFileHeader> getBamFileHeaders(String[] bams) {
		List<SAMFileHeader> headers = new ArrayList<SAMFileHeader>();
		for (String bam : bams) {
			SAMFileHeader header = SAMFileReaderFactory.createSAMFileReader(bam).getFileHeader();
			headers.add(header);
		}
		return headers;
	}
	
	private void checkHeadersSortOrder(List<SAMFileHeader> headers, boolean isNormal) throws SnpException {
		for (SAMFileHeader sfh : headers) {
			if (SAMFileHeader.SortOrder.coordinate != sfh.getSortOrder()) {
				throw new SnpException("BAM_FILE_NOT_SORTED", (isNormal ? "Control" : "Test"));
			}
		}
	}
	
	private List<SAMSequenceDictionary> getSequenceDictionaries(List<SAMFileHeader> headers) {
		List<SAMSequenceDictionary> seqDictionaries = new ArrayList<SAMSequenceDictionary>();
		for (SAMFileHeader header : headers) {
			seqDictionaries.add(header.getSequenceDictionary());
		}
		return seqDictionaries;
	}
	
	private void checkSequenceDictionaries(List<SAMFileHeader> normalHeaders, List<SAMFileHeader> tumourHeaders) throws SnpException {
		List<SAMSequenceDictionary> normalSeqDictionaries = getSequenceDictionaries(normalHeaders);
		List<SAMSequenceDictionary> tumourSeqDictionaries = getSequenceDictionaries(tumourHeaders);
		
		for (SAMSequenceDictionary normalSD : normalSeqDictionaries) {
			List<SAMSequenceRecord> normalSequences = normalSD.getSequences();
			
			for (SAMSequenceDictionary tumourSD : tumourSeqDictionaries) {
				if (normalSD.getReferenceLength() != tumourSD.getReferenceLength()) {
					throw new SnpException("SEQUENCE_LENGTHS_DONT_MATCH"
							, ""+normalSD.getReferenceLength() , ""+tumourSD.getReferenceLength());
				}
				
				List<SAMSequenceRecord> tumourSequences = tumourSD.getSequences();
				int i = 0;
				for (SAMSequenceRecord normalSeq : normalSequences) {
					SAMSequenceRecord tumourSeq = tumourSequences.get(i++);
					if ( ! normalSeq.isSameSequence(tumourSeq) ) {
						throw new SnpException("SEQUENCES_DONT_MATCH", 
								new String[] {normalSeq.getSequenceName(), normalSeq.getSequenceLength()+"",
								tumourSeq.getSequenceName(), tumourSeq.getSequenceLength()+""});	
					}
				}
			}
		}
		
		
		// pick the first normal sequence to check against fasta
		List<SAMSequenceRecord> normalSequences = normalSeqDictionaries.get(0).getSequences();
		
		// now check against the supplied reference file
		FastaSequenceFile ref = new FastaSequenceFile(new File(referenceFile), true);
		if (null != ref) {
			try {
				ReferenceSequence nextRefSeq = null;
				
				// loop through the normal sequences, compare name and size against supplied reference
				for (SAMSequenceRecord normalSeq : normalSequences) {
					String name = normalSeq.getSequenceName();
					int length = normalSeq.getSequenceLength();
					
					// get next sequence
					nextRefSeq = ref.nextSequence();
					if (null == nextRefSeq) {
						logger.warn("Mismatch in number of sequences - no more reference sequences, normal sequence: " + name + ":" + length);
						break;
					} else {
						
						// if the sequence names don't match - throw an exception
						// if the lengths don't match - log a warning message
						if ( ! nextRefSeq.getName().equalsIgnoreCase(name)) {
							logger.error("reference sequence name (" + nextRefSeq.getName() + ") does not match normal bam file sequence name (" + name + ")");
							throw new SnpException("SEQUENCES_DONT_MATCH", 
									new String[] {name, length+"",
									nextRefSeq.getName(), nextRefSeq.length()+""});	
						} else if (nextRefSeq.length() != length) {
							logger.warn("Reference sequence lengths don't match. Normal sequence: " 
									+ name + ":" + length + ", Reference sequence: " 
									+ nextRefSeq.getName() + ":" + nextRefSeq.length());
						}
					}
				}
			} finally {
				ref.close();
			}
		}
	}
	
	/**
	 * This method aims to determine if the references used to map the normal and tumour bams are the same
	 * It will also check the sort order of the bam files.
	 * And finally, some checking against the supplied reference file is performed
	 * 
	 * @throws SnpException 
	 */
	void checkBamHeaders() throws SnpException {
		logger.info("Checking bam file headers for reference file compatibility");
		
		List<SAMFileHeader> normalHeaders = null;
		if ( ! singleSampleMode) {
			normalHeaders = getBamFileHeaders(controlBams);
			checkHeadersSortOrder(normalHeaders, true);
		}
		List<SAMFileHeader> tumourHeaders = getBamFileHeaders(testBams);
		
		// check sort order
		checkHeadersSortOrder(tumourHeaders, false);
		
		if ( ! singleSampleMode) {
			checkSequenceDictionaries(normalHeaders, tumourHeaders);
		}
		logger.info("Checking bam file headers for reference file compatibility - DONE");
	}
	
	/**
	 * For SOMATIC positions, that don't currently have evidence of the mutation in the normal, examine
	 * the unfiltered normal to see if any evidence exists there
	 * 
	 */
	void incorporateUnfilteredNormal() {
		int noOfAffectedRecords = 0;
		for (Entry<ChrPosition, QSnpRecord> entry : positionRecordMap.entrySet()) {
			QSnpRecord record = entry.getValue();
			if (Classification.SOMATIC == record.getClassification()
					&& (null == record.getAnnotation() 
					|| ! record.getAnnotation().contains(SnpUtils.MUTATION_IN_NORMAL))	// PASS will pass this test :)
					&& ! StringUtils.isNullOrEmpty(record.getUnfilteredNormalPileup())) {
				
				char alt = record.getMutation().charAt(record.getMutation().length()-1);
				
				if (record.getUnfilteredNormalPileup().indexOf(Character.toUpperCase(alt)) > -1) {
					VcfUtils.updateFilter(record.getVcfRecord(), SnpUtils.MUTATION_IN_UNFILTERED_NORMAL);
					noOfAffectedRecords++;
				}
			}
		}
		
		logger.info("number of SOMATIC snps that have evidence of mutation in unfiltered normal: " + noOfAffectedRecords);
	}
	
	public  VcfRecord convertQSnpToVCF(QSnpRecord rec) {
//		VCFRecord vcf = VcfUtils.createVcfRecord(rec.getChrPos(), rec.getDbSnpId(), rec.getRef() + "");
		VcfRecord vcf = rec.getVcfRecord();
		
		String altString = null != rec.getMutation() ? SnpUtils.getAltFromMutationString(rec.getMutation()) : null;
//		char alt = '\u0000';
//		if (null == altString || altString.length() > 1) {
//			logger.warn("altString: " + altString + " in Pipeline.convertQSnpToVCF");
//		} else {
//			alt = altString.charAt(0);
//		}
		final int mutantReadCount = SnpUtils.getCountFromNucleotideString(
				Classification.GERMLINE != rec.getClassification() ? rec.getTumourNucleotides() : rec.getNormalNucleotides(), altString);
		final int novelStartCount = Classification.GERMLINE != rec.getClassification() 
					? rec.getTumourNovelStartCount() : rec.getNormalNovelStartCount();
		
					
		vcf.addFilter(rec.getAnnotation());		// don't overwrite existing annotations
		
		String info = "";
//		StringBuilder info = new StringBuilder();
		if (Classification.SOMATIC == rec.getClassification()) {
//			info.append(rec.getClassification().toString());
			info = StringUtils.addToString(info, Classification.SOMATIC.toString(), Constants.SEMI_COLON);
		}
		
		// add Number of Mutations (MR - Mutated Reads)
		if (mutantReadCount > 0) {
//			if (info.length() > 0) {
//				info.append(Constants.SEMI_COLON);
//			}
//			info.append(VcfUtils.INFO_MUTANT_READS).append(Constants.EQ).append(mutantReadCount);
			info = StringUtils.addToString(info,VcfHeaderUtils.INFO_MUTANT_READS +Constants.EQ +mutantReadCount  , Constants.SEMI_COLON);
		}
		
		if (novelStartCount > 0) {
//			if (info.length() > 0) {
//				info.append(Constants.SEMI_COLON);
//			}
//			info.append(VcfUtils.INFO_NOVEL_STARTS).append(Constants.EQ).append(novelStartCount);
			info = StringUtils.addToString(info, VcfHeaderUtils.INFO_NOVEL_STARTS +Constants.EQ +novelStartCount  , Constants.SEMI_COLON);
		}
		
		// cpg data
		if ( ! StringUtils.isNullOrEmpty(rec.getFlankingSequence())) {
//			if (info.length() > 0) {
//				info.append(Constants.SEMI_COLON);
//			}
//			info.append(VcfUtils.INFO_FLANKING_SEQUENCE).append(Constants.EQ).append(rec.getFlankingSequence());
			info = StringUtils.addToString(info, VcfHeaderUtils.INFO_FLANKING_SEQUENCE +Constants.EQ +rec.getFlankingSequence()  , Constants.SEMI_COLON);
		}
		
		String [] altAndGTs = VcfUtils.getMutationAndGTs(rec.getRef(), rec.getNormalGenotype(), rec.getTumourGenotype());
		vcf.setAlt(altAndGTs[0]);
		
		
		
		// get existing format field info from vcf record
		List<String> additionalformatFields = new ArrayList<>();
		
		// FORMAT field - contains GT field (and others)
		StringBuilder formatField = new StringBuilder();
		// add in the columns
		formatField.append(VcfHeaderUtils.FORMAT_GENOTYPE).append(Constants.COLON);
		formatField.append(VcfHeaderUtils.FORMAT_GENOTYPE_DETAILS).append(Constants.COLON);
		formatField.append(VcfHeaderUtils.FORMAT_ALLELE_COUNT);
		additionalformatFields.add(formatField.toString());
		
		String normalGDField = null != rec.getNormalGenotype() ? rec.getNormalGenotype().getDisplayString() : Constants.MISSING_DATA_STRING;
		String tumourGDField = null != rec.getTumourGenotype() ? rec.getTumourGenotype().getDisplayString() : Constants.MISSING_DATA_STRING;
		
		// add in normal format details first, then tab, then tumour
		if ( ! singleSampleMode) {
			formatField.setLength(0);
			formatField.append(altAndGTs[1]).append(Constants.COLON);
			formatField.append(normalGDField).append(Constants.COLON);
			String nNucleotides = StringUtils.isNullOrEmpty(rec.getNormalNucleotides()) ? Constants.MISSING_DATA_STRING : rec.getNormalNucleotides(); 
			
			formatField.append(nNucleotides.replace(":", ""));// remove colons in nucleotide strings
//			formatField.append(Constants.TAB);
//			vcf.addFormatField(1, formatField.toString());
			additionalformatFields.add(formatField.toString());
		}
		
		// tumour
		String tNucleatides = StringUtils.isNullOrEmpty(rec.getTumourNucleotides()) ? Constants.MISSING_DATA_STRING : rec.getTumourNucleotides();
		formatField.setLength(0);
		formatField.append(altAndGTs[2]).append(Constants.COLON);
		formatField.append(tumourGDField).append(Constants.COLON);
		formatField.append(tNucleatides.replace(":", ""));// remove colons in nucleotide strings
		additionalformatFields.add(formatField.toString());
		
		VcfUtils.addFormatFieldsToVcf(vcf, additionalformatFields);
//		vcf.addFormatField(singleSampleMode ? 1 : 2, formatField.toString());
		vcf.addInfo(info);
//		vcf.setFormatField(formatField.toString());
		return vcf;
	}
	
	/**
	 * TODO
	 * add the appropriate flag should the motif be found
	 * 
	 */
	void incorporateCPGData() {
		int noOfAffectedRecords = 0;
		for (QSnpRecord record : positionRecordMap.values()) {
			
			String cpg = record.getFlankingSequence(); 
			if (null != cpg) {
				/*
				 * if (motif is in cpg)
				 * 	noOfAffectedRecords++;
				 */
			}
		}
		
		logger.info("number of snps that have evidence of mutation in unfiltered normal: " + noOfAffectedRecords);
	}
	
	void parsePileup(String record) throws IOException {
		String[] params = TabTokenizer.tokenize(record);

		// get coverage for both normal and tumour
		int normalCoverage = PileupUtils.getCoverageCount(params, controlStartPositions);
		int tumourCoverage = PileupUtils.getCoverageCount(params, testStartPositions);
		
		if (normalCoverage + tumourCoverage < initialTestSumOfCountsLimit) return;

		String normalBases = PileupUtils.getBases(params, controlStartPositions);
		String tumourBases = PileupUtils.getBases(params, testStartPositions);
		
		if ( ! includeIndels) {
			// means there is an indel at this position - ignore
			if (PileupUtils.doesPileupContainIndel(normalBases)) return;
			if (PileupUtils.doesPileupContainIndel(tumourBases)) return;
		}
		
		String normalBaseQualities = PileupUtils.getQualities(params, controlStartPositions);
		String tumourBaseQualities = PileupUtils.getQualities(params, testStartPositions);

		// get bases as PileupElement collections
		List<PileupElement> normalBaseCounts = PileupElementUtil.getPileupCounts(normalBases, normalBaseQualities);
		List<PileupElement> tumourBaseCounts = PileupElementUtil.getPileupCounts(tumourBases, tumourBaseQualities);

		// get variant count for both
		int normalVariantCount = PileupElementUtil.getLargestVariantCount(normalBaseCounts);
		int tumourVariantCount = PileupElementUtil.getLargestVariantCount(tumourBaseCounts);
		
		// new - hope this doesn't bugger things up!!
		// only proceed if we actually have at least 1 variant
		if (normalVariantCount + tumourVariantCount == 0) return;
		
		// get rule for normal and tumour
		Rule normalRule = RulesUtil.getRule(controlRules, normalCoverage);
		Rule tumourRule = RulesUtil.getRule(testRules, tumourCoverage);
		
		
		final boolean normalFirstPass = isPileupRecordAKeeperFirstPass(normalVariantCount, normalCoverage, normalRule, normalBaseCounts, baseQualityPercentage);;
		boolean normalSecondPass = false;
		boolean tumourFirstPass = false;
		boolean tumourSecondPass = false;
		
		if ( ! normalFirstPass) {
			normalSecondPass = isPileupRecordAKeeperSecondPass(normalVariantCount, normalCoverage, normalRule, normalBaseCounts, baseQualityPercentage);
			
			if ( ! normalSecondPass) {
				tumourFirstPass = isPileupRecordAKeeperFirstPass(tumourVariantCount, tumourCoverage, tumourRule, tumourBaseCounts, baseQualityPercentage);
				
				if ( ! tumourFirstPass) {
					tumourSecondPass = isPileupRecordAKeeperSecondPass(tumourVariantCount, tumourCoverage, tumourRule, tumourBaseCounts, baseQualityPercentage);
				}
			}
		}
		
		// only keep record if it has enough variants
		if (normalFirstPass || normalSecondPass || tumourFirstPass || tumourSecondPass) {
			
			// if normal passed, need to test tumour to see what rule to use
			if (normalFirstPass || normalSecondPass) {
				tumourFirstPass = isPileupRecordAKeeperFirstPass(tumourVariantCount, tumourCoverage, tumourRule, tumourBaseCounts, baseQualityPercentage);
				
				if ( ! tumourFirstPass) {
					tumourSecondPass = isPileupRecordAKeeperSecondPass(tumourVariantCount, tumourCoverage, tumourRule, tumourBaseCounts, baseQualityPercentage);
				}
			}

			//TODO check that this is right (param[3] == alt)
			QSnpRecord qRecord = new QSnpRecord(params[0], Integer.parseInt(params[1]), params[2], params[3]);
			qRecord.setPileup(record);
			// setup some values on the record
//			qRecord.setRef(params[2].charAt(0));
			qRecord.setNormalCount(normalCoverage);
			qRecord.setTumourCount(tumourCoverage);
			
			
			String refString = qRecord.getRef();
			if (refString.length() > 1) {
				logger.warn("refString: " + refString + " in Pipeline.parePileup");
			}
			char ref = refString.charAt(0) ;
			
			// set normal pileup to only contain the different bases found in normal, rather than the whole pileup string
			// which contains special chars indicating start/end of reads along with mapping qualities
			if (normalCoverage > 0)
				qRecord.setNormalPileup(PileupElementUtil.getBasesFromPileupElements(normalBaseCounts, ref));

			// use all base counts to form genotype
			List<PileupElement> normalBaseCountsPassRule = PileupElementUtil
				.getPileupCountsThatPassRule(normalBaseCounts, normalRule, normalSecondPass, baseQualityPercentage);
			List<PileupElement> tumourBaseCountsPassRule = PileupElementUtil
				.getPileupCountsThatPassRule(tumourBaseCounts, tumourRule, tumourSecondPass, baseQualityPercentage);
			
			qRecord.setNormalGenotype(PileupElementUtil.getGenotype(normalBaseCountsPassRule, ref));
			qRecord.setTumourGenotype(PileupElementUtil.getGenotype(tumourBaseCountsPassRule, ref));
			
			qRecord.setNormalNucleotides(PileupElementUtil.getPileupElementString(normalBaseCounts, ref));
			qRecord.setTumourNucleotides(PileupElementUtil.getPileupElementString(tumourBaseCounts, ref));
			
			// set Id
			qRecord.setId(++mutationId);
			positionRecordMap.put(new ChrPosition(qRecord.getChromosome(), qRecord.getPosition()), qRecord);
		}
	}
	
	
	static boolean isPileupRecordAKeeperFirstPass(int variantCount, int coverage, Rule rule, List<PileupElement> baseCounts, double percentage) {
		// if rule is null, return false
		if (null == rule) return false;
		// first check to see if it passes the rule
		return PileupElementUtil.passesCountCheck(variantCount, coverage, rule) && PileupElementUtil.passesWeightedVotingCheck(baseCounts, percentage);
	}
	
	static boolean isPileupRecordAKeeperSecondPass(int variantCount, int coverage, Rule rule, List<PileupElement> baseCounts, double percentage) {
		// if rule is null, return false
		if (null == rule) return false;
		// first check to see if it passes the rule
		return PileupElementUtil.passesCountCheck(variantCount, coverage, rule, true) 
				&& isVariantOnBothStrands(baseCounts) 
				&& PileupElementUtil.passesWeightedVotingCheck(baseCounts, percentage, true);
	}
	
	static boolean isPileupRecordAKeeper(int variantCount, int coverage, Rule rule, List<PileupElement> baseCounts, double percentage) {
		if (isPileupRecordAKeeperFirstPass(variantCount, coverage, rule, baseCounts, percentage))
			return true;
		else return isPileupRecordAKeeperSecondPass(variantCount, coverage, rule, baseCounts, percentage);
	}
	
	static boolean isVariantOnBothStrands(List<PileupElement> baseCounts) {
		PileupElement pe = PileupElementUtil.getLargestVariant(baseCounts);
		return null == pe ? false :  pe.isFoundOnBothStrands();
	}
	
	/**
	 * Assumes normal bams come before tumour bams in the pileup file
	 */
	void getStringPositions() {
		controlStartPositions = PileupUtils.getStartPositions(noOfControlFiles, noOfTestFiles, true);
		testStartPositions =PileupUtils.getStartPositions(noOfControlFiles, noOfTestFiles, false);
	}
	
	void checkRules() throws SnpException {
		String rulesErrors = RulesUtil.examineRules(controlRules);
		if (null != rulesErrors) {
			logger.warn("Problem with Normal Rules: " + rulesErrors);
		}
		
		rulesErrors = RulesUtil.examineRules(testRules);
		if (null != rulesErrors) {
			logger.warn("Problem with Tumour Rules: " + rulesErrors);
		}
	}
	
	void loadNextReferenceSequence() {
		if (null == sequenceFile) {
			sequenceFile = new FastaSequenceFile(new File(referenceFile), true);
		}
		referenceBases = null;
		currentChr = null;
		referenceBasesLength = 0;
		ReferenceSequence refSeq = sequenceFile.nextSequence();
		
		if (null == refSeq) {	// end of the line
			logger.info("No more chromosomes in reference file - shutting down");
			closeReferenceFile();
		} else {
			currentChr = refSeq.getName();
			referenceBases = refSeq.getBases();
			referenceBasesLength = refSeq.length();
			logger.info("Will process records from: " + currentChr + ", length: " + referenceBasesLength);
		}
	}
	void closeReferenceFile() {
		if (null != sequenceFile) sequenceFile.close();
	}
	
	/**
	 * use the available threads to get the fisher exact test two tailed pvalues into the probability field of the qsnp records
	 */
	void populateProbabilities() {
		logger.info("About to hit Fisher Exact Test two tailed pvalue");
		final Queue<QSnpRecord> queue = new ConcurrentLinkedQueue<QSnpRecord>();
		for (QSnpRecord rec : positionRecordMap.values()) {
			queue.add(rec);
		}
		
		int noOfThreadsToUse = 5;
		ExecutorService service = Executors.newFixedThreadPool(noOfThreadsToUse);
		for (int i = 0 ; i < noOfThreadsToUse ; i++) {
			service.execute(new Runnable() {

				@Override
				public void run() {
					// take a QSnpRecord, if none left we are done
					while (true) {
						QSnpRecord record = queue.poll();
						if (null == record) break;
						
						String mutation = record.getMutation();
						if (StringUtils.isNullOrEmpty(mutation)) continue;
						
						String ref = record.getRef();
						String alt = SnpUtils.getAltFromMutationString(mutation);
						
						int aNormalAlt = SnpUtils.getCountFromNucleotideString(record.getNormalNucleotides(), alt);
						int bTumourAlt = SnpUtils.getCountFromNucleotideString(record.getTumourNucleotides(), alt);
						int cNormalRef = SnpUtils.getCountFromNucleotideString(record.getNormalNucleotides(), ref);
						int dTumourRef = SnpUtils.getCountFromNucleotideString(record.getTumourNucleotides(), ref);
						
						double pValue = FisherExact.getTwoTailedFET(aNormalAlt, bTumourAlt, cNormalRef, dTumourRef);
	//					logger.info("pvalue for following values (a,b,c,d:pvalue): " + normalAlt + "," + tumourAlt + "," + normalRef + "," + tumourRef + ": " + pValue);
						record.setProbability(pValue);
					}
				}});
		}
		service.shutdown();
		
		try {
			service.awaitTermination(10, TimeUnit.HOURS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		logger.info("About to hit Fisher Exact Test two tailed pvalue - DONE");
	}
	
	void walkBams() throws Exception {
		walkBams(includeDuplicates);
	}
	
	/**
	 * Sets up 2 Producer threads, 2 Consumer threads and a Cleaner thread, along with the concurrent collections, queues, and barriers used by them all
	 * 
	 * @param ignoreDuplicates indicates whether duplicate records should be discarded out right. Not useful for torrent mode
	 * @throws Exception
	 */
	void walkBams(boolean includeDups) throws Exception {
		logger.info("About to hit bam files");
		
		int noOfThreads = singleSampleMode ? 3 : 5;		// 2 for each bam, and a single cleaner
		int consumerLatchSize = singleSampleMode ? 1 : 2;		// 2 for each bam, and a single cleaner
		
		
		final AtomicInteger controlMinStart = new AtomicInteger();
		final AtomicInteger testMinStart = new AtomicInteger();
		final Queue<SAMRecordFilterWrapper> normalSAMQueue = new ConcurrentLinkedQueue<SAMRecordFilterWrapper>();
		final Queue<SAMRecordFilterWrapper> tumourSAMQueue = new ConcurrentLinkedQueue<SAMRecordFilterWrapper>();
		
		// used by Cleaner3 threads
		final ConcurrentMap<Integer, Accumulator> cnormalAccs = new ConcurrentHashMap<Integer, Accumulator>();
		final ConcurrentMap<Integer, Accumulator> ctumourAccs = new ConcurrentHashMap<Integer, Accumulator>(1024 * 1024);
		
		final CyclicBarrier barrier = new CyclicBarrier(noOfThreads, new Runnable() {
			@Override
			public void run() {
				// reset the minStartPositions values to zero
				controlMinStart.set(0);
				testMinStart.set(0);
				
				// update the reference bases array
				loadNextReferenceSequence();
				logger.info("barrier has been reached by all threads - moving onto next chromosome");
			}
		});
		ExecutorService service = Executors.newFixedThreadPool(noOfThreads);
		CountDownLatch consumerLatch = new CountDownLatch(consumerLatchSize);
		CountDownLatch controlProducerLatch = new CountDownLatch(1);
		CountDownLatch testProducerLatch = new CountDownLatch(1);
		CountDownLatch cleanerLatch = new CountDownLatch(1);
		
		
		// Control threads (if not single sample)
		if ( ! singleSampleMode) {
			service.execute(new Producer(controlBams, controlProducerLatch, true, normalSAMQueue, Thread.currentThread(), query, barrier, includeDups));
			service.execute(new Consumer(consumerLatch, controlProducerLatch, testProducerLatch, true, 
					Thread.currentThread(), barrier, cnormalAccs, normalSAMQueue, controlMinStart));
		}
		
		// test threads
		service.execute(new Producer(testBams, testProducerLatch, false, tumourSAMQueue, Thread.currentThread(), query, barrier, includeDups));
		service.execute(new Consumer(consumerLatch, controlProducerLatch, testProducerLatch, false, 
				Thread.currentThread(), barrier, ctumourAccs, tumourSAMQueue, testMinStart));
		
		// Cleaner
		service.execute(new Cleaner(cleanerLatch, consumerLatch, Thread.currentThread(),
				barrier, controlMinStart, testMinStart, cnormalAccs, ctumourAccs));
		
		service.shutdown();
		try {
			if ( ! singleSampleMode) {
				controlProducerLatch.await();
			}
			testProducerLatch.await();
			consumerLatch.await();
			cleanerLatch.await();
		} catch (InterruptedException e) {
			logger.info("current thread about to be interrupted...");
			logger.error("Error in thread: ", e);
			
			// kill off any remaining threads
			service.shutdownNow();
			
			logger.error("Terminating due to failed Producer/Consumer/Cleaner threads");
			throw e;
		}
		logger.info("bam file access finished!");
	}
	
	
	public class Producer implements Runnable {
		
		private final MultiSAMFileReader reader;
		private final MultiSAMFileIterator iter;
		private final boolean isControl;
		private final CountDownLatch latch;
		private final Queue<SAMRecordFilterWrapper> queue;
		private QueryExecutor qbamFilter;
		private final Thread mainThread;
		private long passedFilterCount = 0;
		private long invalidCount = 0;
		private long counter = 0;
		private final CyclicBarrier barrier;
		private final boolean includeDups;
		private final boolean runqBamFilter;
		
		public Producer(final String[] bamFiles, final CountDownLatch latch, final boolean isNormal, 
				final Queue<SAMRecordFilterWrapper> samQueue, final Thread mainThread, final String query, 
				final CyclicBarrier barrier, boolean includeDups) throws Exception {
			this.latch = latch;
			Set<File> bams = new HashSet<File>();
			for (String bamFile : bamFiles) {
				bams.add(new File(bamFile));
			}
			this.reader = new MultiSAMFileReader(bams, true, validation);
			this.iter = reader.getMultiSAMFileIterator();
			this.isControl = isNormal;
			this.mainThread = mainThread;
			this.queue = samQueue;
			if ( ! StringUtils.isNullOrEmpty(query) && ! "QCMG".equals(query))
				qbamFilter = new QueryExecutor(query);
			this.barrier = barrier;
			this.includeDups = includeDups;
			runqBamFilter = null != qbamFilter;
		}

		@Override
		public void run() {
			logger.info("In Producer run method with isControl: " + isControl);
			logger.info("Use qbamfilter? " + runqBamFilter);
			try {
				
				while (iter.hasNext()) {
					SAMRecord record = iter.next();
					
					if (++ counter % 1000000 == 0) {
						int qSize = queue.size();
						logger.info("hit " + counter/1000000 + "M sam records, passed filter: " + passedFilterCount + ", qsize: " + qSize);
						if (passedFilterCount == 0 && counter >= noOfRecordsFailingFilter) {
							throw new SnpException("INVALID_FILTER", ""+counter);
						}
						while (qSize > 10000) {
							try {
								Thread.sleep(200);
							} catch (InterruptedException e) {
								logger.warn("InterruptedException caught whilst Producer thread was sleeping");
								throw e;
							}
							qSize = queue.size();
						}
					}
					
					if (record.getReferenceName().equals(currentChr)) {
						processRecord(record);
						
						
					} else if (null == currentChr) {
						// no longer have reference details - exit
						logger.warn("Exiting Producer despite records remaining in file - null reference chromosome");
						logger.warn("extra record: " + SAMUtils.getSAMRecordAsSting(record));
						break;
					} else {
						logger.info("Producer: Processed all records in " + currentChr + ", waiting at barrier");
						try {
							barrier.await();
							// don't need to reset barrier, threads waiting at barrier are released when all threads reach barrier... oops
//							if (isNormal) barrier.reset();		// reset the barrier
						} catch (InterruptedException e) {
							logger.error("Producer: InterruptedException exception caught whilst processing record: " + SAMUtils.getSAMRecordAsSting(record), e);
							throw e;
						} catch (BrokenBarrierException e) {
							logger.error("Producer: BrokenBarrier exception caught whilst processing record: " + SAMUtils.getSAMRecordAsSting(record), e);
							throw e;
						}
						
						// wait until queues are empty
						int qSize = queue.size();
						if (qSize > 0)
							logger.info("Waiting for empty queue before continuing with next chr. qsize: " + qSize);
						while (qSize > 0) {
							try {
								Thread.sleep(100);
							} catch (InterruptedException e) {
								e.printStackTrace();
								throw e;
							}
							qSize = queue.size();
						}
						// deal with this record
						processRecord(record);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				mainThread.interrupt();
			} finally {
				latch.countDown();
				logger.info("Producer: shutting down - processed " + counter + " records, passed filter: " 
						+ passedFilterCount + ", invalidCount: " + invalidCount);
			}
		}
		
		private void processRecord(SAMRecord record) throws Exception {
			
			// if record is not valid for variatn calling - discard
			if ( ! SAMUtils.isSAMRecordValidForVariantCalling(record, includeDups)) {
				invalidCount++;
				return;
			}
			
			if (runqBamFilter) {
				boolean passesFilter = qbamFilter.Execute(record);
				if (isControl || passesFilter) {
					addRecordToQueue(record, counter, passesFilter);
				}
			} else {
				// didn't have any filtering defined - add all
				addRecordToQueue(record, counter, true);
			}
		}
		
		private void addRecordToQueue(final SAMRecord record, final long counter, final boolean passesFilter) {
			
			record.getReadBases();					// cache read bases in object
			if (passesFilter) {
				record.getBaseQualities();	// cache base qualities in object
				passedFilterCount++;
			}
			record.getCigar();					// cache cigar for all records
			record.getAlignmentEnd();		// cache alignment end for all records
//			if (record.getReadNegativeStrandFlag()) record.getAlignmentEnd();		// cache alignment end if its on reverse strand
			
			final SAMRecordFilterWrapper wrapper = new SAMRecordFilterWrapper(record, counter);
			wrapper.setPassesFilter(passesFilter);
			queue.add(wrapper);
		}
	}
	
	public class Consumer implements Runnable {
		
		private final CountDownLatch consumerLatch;
		private final CountDownLatch controlLatch;
		private final CountDownLatch testLatch;
		private final boolean isControl;
		private final Thread mainThread;
		private final  ConcurrentMap<Integer, Accumulator> map;
		private final CyclicBarrier barrier;
		private final Queue<SAMRecordFilterWrapper> queue;
		private final AtomicInteger minStartPosition;
//		private final boolean singleSampleMode;
		
		private final int maxMapSize = 100000;
		
		public Consumer(final CountDownLatch consumerLatch, final CountDownLatch normalLatch, 
				final CountDownLatch tumourLatch, final boolean isNormal, final Thread mainThread, 
				final CyclicBarrier barrier, final ConcurrentMap<Integer, Accumulator> map,
				final Queue<SAMRecordFilterWrapper> queue, final AtomicInteger minStartPosition){
			
			this.consumerLatch = consumerLatch;
			this.controlLatch = normalLatch;
			this.testLatch = tumourLatch;
			this.isControl = isNormal;
			this.mainThread = mainThread;
			this.map =  map;
			this.barrier = barrier;
			this.queue = queue;
			this.minStartPosition = minStartPosition;
		}
		
		private void processSAMRecord(final SAMRecordFilterWrapper record) {
			final SAMRecord sam = record.getRecord();
			final boolean forwardStrand = ! sam.getReadNegativeStrandFlag();
			final int startPosition = sam.getAlignmentStart();
			// endPosition is just that for reverse strand, but for forward strand reads it is start position
			final int endPosition = sam.getAlignmentEnd();
			final byte[] bases = sam.getReadBases();
			final byte[] qualities = record.getPassesFilter() ? sam.getBaseQualities() : null;
			final Cigar cigar = sam.getCigar();
			
			int referenceOffset = 0, offset = 0;
			
			for (CigarElement ce : cigar.getCigarElements()) {
				CigarOperator co = ce.getOperator();
				int length = ce.getLength();

				if (co.consumesReferenceBases() && co.consumesReadBases()) {
					// we have a number (length) of bases that can be advanced.
					updateMapWithAccums(startPosition, bases,
							qualities, forwardStrand, offset, length, referenceOffset, 
							record.getPassesFilter(), endPosition, record.getPosition());
					// advance offsets
					referenceOffset += length;
					offset += length;
				} else if (co.consumesReferenceBases()) {
					// DELETION
					referenceOffset += length;
				} else if (co.consumesReadBases()){
					// INSERTION, SOFT CLIPPING
					offset += length;
				}
			}
		}
		
		/**
		 * 
		 * @param startPosition start position as reported on the forward strand (getAlignmentStart)
		 * @param bases
		 * @param qualities
		 * @param forwardStrand
		 * @param offset
		 * @param length
		 * @param referenceOffset
		 * @param passesFilter
		 * @param readStartPosition start position of the read - depends on strand as to whether this is the alignemtnEnd or alignmentStart
		 */
		public void updateMapWithAccums(int startPosition, final byte[] bases, final byte[] qualities,
				boolean forwardStrand, int offset, int length, int referenceOffset, final boolean passesFilter, final int readEndPosition, long readId) {
			
			final int startPosAndRefOffset = startPosition + referenceOffset;
			
			for (int i = 0 ; i < length ; i++) {
				Accumulator acc = map.get(i + startPosAndRefOffset);
				if (null == acc) {
					acc = new Accumulator(i + startPosAndRefOffset);
					Accumulator oldAcc = map.putIfAbsent(i + startPosAndRefOffset, acc);
					if (null != oldAcc) acc = oldAcc;
				}
				if (passesFilter) {
					acc.addBase(bases[i + offset], qualities[i + offset], forwardStrand, 
							startPosition, i + startPosAndRefOffset, readEndPosition, readId);
				} else {
					acc.addUnfilteredBase(bases[i + offset]);
				}
			}
		}
		
		@Override
		public void run() {
			logger.info("In Consumer run method with isControl: " + isControl);
			try {
				long count = 0;
				while (true) {
					
					final SAMRecordFilterWrapper rec = queue.poll();
					if (null != rec) {
						
						processSAMRecord(rec);
//						int alignmentStart = rec.getRecord().getAlignmentStart(); 
						minStartPosition.set(rec.getRecord().getAlignmentStart());
						
						if (++count % maxMapSize == 0) {
							int mapSize = map.size();
							if (mapSize > maxMapSize) {
								
//								stealWork();
								
								
								for (int i = 0 ; i < 100 ; i++) {
									final int sleepInterval = mapSize / 1000;
									if (sleepInterval > 1000) logger.info("sleeping for " + sleepInterval + ", map size: " + mapSize);
									try {
										Thread.sleep(sleepInterval);
									} catch (InterruptedException e) {
										logger.error("InterruptedException caught in Consumer sleep",e);
										throw e;
									}
									if (map.size() < maxMapSize) break;
								}
								mapSize = map.size();
								if (mapSize > maxMapSize) {
									logger.warn("map size still over 100000 despite multiple sleeps: " + mapSize);
								}
							}
						}
					} else {
						if ((singleSampleMode || controlLatch.getCount() == 0) && testLatch.getCount() == 0) {
							break;
						}
						// check the barrier - could be zero
						if (barrier.getNumberWaiting() >= (singleSampleMode ? 1 : 2)) {
//							logger.info("null record, barrier count > 2 - what now??? q.size: " + queue.size());
							// just me left
							if (queue.size() == 0 ) {
								logger.info("Consumer: Processed all records in " + currentChr + ", waiting at barrier");
								
								try {
									barrier.await();
									assert map.isEmpty() : "Consumer: map was not empty following barrier reset";
									count = 0;
								} catch (InterruptedException e) {
									logger.error("Consumer: InterruptedException caught with map size: " + map.size(), e);
									throw e;
								} catch (BrokenBarrierException e) {
									logger.error("Consumer: BrokenBarrierException caught with map size: " + map.size(), e);
									throw e;
								}
							}
						} else {
							// sleep and try again
							try {
								Thread.sleep(50);
							} catch (InterruptedException e) {
								logger.error("InterruptedException caught in Consumer sleep",e);
								throw e;
							}
						}
					}
				}
			} catch (Exception e) {
				logger.error("Exception caught in Consumer thread: ", e);
				mainThread.interrupt();
			} finally {
				consumerLatch.countDown();
				logger.info("Consumer: shutting down");
			}
		}

//		private void stealWork() {
//			// check that work steal map is not too full
//			logger.info("Stealing work...");
//			while ( map.size() > maxMapSize && workStealMap.size() < maxMapSize) {
//				final SAMRecordFilterWrapper rec = workStealQueue.poll();
//				if (null != rec) {
//					processSAMRecord(rec, true);
////					workStealMinStartPosition.set(rec.getRecord().getAlignmentStart());
//				}
//			}
//		}
	}
	
	
	public class Cleaner implements Runnable {
		private final CountDownLatch cleanerLatch;
		private final CountDownLatch consumerLatch;
		private final Thread mainThread;
		private int previousPosition = 0;
		private final CyclicBarrier barrier;
		private final AtomicInteger controlMinStart;
		private final AtomicInteger testMinStart;
		private final  ConcurrentMap<Integer, Accumulator> controlAccums;
		private final  ConcurrentMap<Integer, Accumulator> testAccums;
		private long processMapsCounter = 0;
		private final boolean debugLoggingEnabled;
		
		public Cleaner(CountDownLatch cleanerLatch, CountDownLatch consumerLatch, Thread mainThread, CyclicBarrier barrier,
				final AtomicInteger normalMinStart, final AtomicInteger tumourMinStart,
				final ConcurrentMap<Integer, Accumulator> cnormalAccs, final ConcurrentMap<Integer, Accumulator> ctumourAccs) {
			this.consumerLatch = consumerLatch;
			this.cleanerLatch = cleanerLatch;
			this.mainThread = mainThread;
			this.barrier = barrier;
			this.controlMinStart = normalMinStart;
			this.testMinStart = tumourMinStart;
			this.controlAccums = cnormalAccs;
			this.testAccums = ctumourAccs;
			debugLoggingEnabled = logger.isLevelEnabled(QLevel.DEBUG);
		}
		
		private void processMaps() {
			final int minStartPos = singleSampleMode ? testMinStart.intValue() -1 : Math.min(controlMinStart.intValue(), testMinStart.intValue()) - 1;
			if (debugLoggingEnabled && ++processMapsCounter % 10 == 0) {
				logger.debug("min start position: " + minStartPos + ", no of keepers so far: " + positionRecordMap.size());
			}
			if (minStartPos <= 0) return;
			final boolean useContainsKey = (minStartPos - previousPosition) > 100000; 
				
			for (int i = previousPosition ; i < minStartPos ; i++) {
				Accumulator controlAcc = singleSampleMode ? null : useContainsKey ? (controlAccums.containsKey(i) ? controlAccums.remove(i) : null) : controlAccums.remove(i);
				Accumulator testAcc = useContainsKey ? (testAccums.containsKey(i) ? testAccums.remove(i) : null) : testAccums.remove(i);
				if (null != testAcc && null != controlAcc) {
					processControlAndTest(controlAcc, testAcc);
				} else if (null != controlAcc){
					processControl(controlAcc);
				} else if (null != testAcc){
					processTest(testAcc);
				}
			}
				
			previousPosition = minStartPos;
		}
		
		private void processMapsAll() {
			if (null != referenceBases && ( ( ! singleSampleMode && ! controlAccums.isEmpty()) || ! testAccums.isEmpty())) {
			
				if ( ! singleSampleMode) {
					for (Map.Entry<Integer, Accumulator> entry : controlAccums.entrySet()) {
						Integer i = entry.getKey();
						Accumulator normAcc = entry.getValue();
						if (i.intValue() >  referenceBasesLength) {
							logger.warn("Found position greater than reference array length: " + i.intValue() + " for chr: " + currentChr);
						} else {
							if (null == normAcc) {
								logger.info("null control acc for key: " + i);
							} else {
								Accumulator tumAcc = testAccums.remove(i);
								if (null != tumAcc) {
									processControlAndTest(normAcc, tumAcc);
								} else {
									processControl(normAcc);
								}
							}
						}
					}
					controlAccums.clear();
				}
				
				// same for tumour
				for (Map.Entry<Integer, Accumulator> entry : testAccums.entrySet()) {
					Integer i = entry.getKey();
					Accumulator tumAcc = entry.getValue();
					if (i.intValue() >  referenceBasesLength) {
						logger.warn("Found position greater than reference array length: " + i.intValue() + " for chr: " + currentChr);
					} else {
						if (null == tumAcc) {
							logger.info("null value for key: " + i);
						} else {
							processTest(tumAcc);
						}
					}
				}
				testAccums.clear();
			}
		}
		
		@Override
		public void run() {
			logger.info("In Cleaner run method");
			try {
				Thread.sleep(500);	// sleep to allow initial population of both queues
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
			
			try {
				while (true) {
					
					processMaps();
						
					if (barrier.getNumberWaiting() == barrier.getParties() - 1) {
						logger.info("Cleaner: about to hit barrier - running processMapsAll");
						
						processMapsAll();
						try {
							previousPosition = 0;
							barrier.await();
							logger.info("Cleaner: no of keepers so far: " + positionRecordMap.size());
							Thread.sleep(500);	// sleep to allow initial map population
						} catch (InterruptedException e) {
							logger.error("InterruptedException caught in Cleaner thread: ", e);
							throw e;
						} catch (BrokenBarrierException e) {
							logger.error("BrokenBarrierException caught in Cleaner thread: ", e);
							throw e;
						}
					} else if (consumerLatch.getCount() == 0) {
						// if latches are shutdown - process remaining items in map and then exit
						logger.info("Cleaner: consumer latch == 0 - running processMapsAll");
						processMapsAll();
						break;
					} else {
						
						try {
							Thread.sleep(5);	
						} catch (InterruptedException e) {
							logger.error("InterruptedException caught in Cleaner sleep",e);
							throw e;
						}
					}
				}
			} catch (Exception e) {
				logger.error("Exception caught in Cleaner thread: ", e);
				mainThread.interrupt();
			} finally {
				cleanerLatch.countDown();
				logger.info("Cleaner: finished - counting down cleanerLatch");
			}
		}
	}
	
	private void processTest(Accumulator testAcc) {
		if (testAcc.containsMultipleAlleles() || 
				(testAcc.getPosition() -1 < referenceBasesLength 
						&& ! baseEqualsReference(testAcc.getBase(),testAcc.getPosition() -1))) {
			interrogateAccumulations(null, testAcc);
		}
	}
	private void processControl(Accumulator controlAcc) {
		if (controlAcc.containsMultipleAlleles() || 
				(controlAcc.getPosition() -1 < referenceBasesLength
						&& ! baseEqualsReference(controlAcc.getBase(), controlAcc.getPosition() -1))) {
			interrogateAccumulations(controlAcc, null);
		}
	}
	private void processControlAndTest(Accumulator controlAcc, Accumulator testAcc) {
		if (controlAcc.containsMultipleAlleles() || testAcc.containsMultipleAlleles() 
				|| (controlAcc.getBase() != testAcc.getBase())
				|| (testAcc.getPosition() -1 < referenceBasesLength 
						&& ! baseEqualsReference(testAcc.getBase(), testAcc.getPosition() -1))
				|| (controlAcc.getPosition() -1 < referenceBasesLength
						&& ! baseEqualsReference(controlAcc.getBase(), controlAcc.getPosition() -1))) {
			interrogateAccumulations(controlAcc, testAcc);
		}
	}
	
	private boolean baseEqualsReference(char base, int position) {
		char refBase = (char) referenceBases[position];
		if (base == refBase) return true;
		if (Character.isLowerCase(refBase)) {
			char upperCaseRef = Character.toUpperCase(refBase);
			return base == upperCaseRef;
		} else return false;
	}
	
	// strand bias check
	void checkForStrandBias(QSnpRecord rec, Accumulator normal, Accumulator tumour, char ref ) {
		if (runSBIASAnnotation) {
			PileupElementLite pel = Classification.GERMLINE != rec.getClassification() ? (null != tumour? tumour.getLargestVariant(ref) : null) : 
				(null != normal ? normal.getLargestVariant(ref) : null);
			
			if (null != pel && ! pel.isFoundOnBothStrands()) {
				VcfUtils.updateFilter(rec.getVcfRecord(), SnpUtils.STRAND_BIAS);
			}
		}
	}
	
	// ends of reads check
	void checkForEndsOfReads(QSnpRecord rec, Accumulator normal, Accumulator tumour, char ref ) {
		
		PileupElementLite pel = Classification.GERMLINE != rec.getClassification() ? (null != tumour? tumour.getLargestVariant(ref) : null) : 
			(null != normal ? normal.getLargestVariant(ref) : null);
		
		if (null != pel && pel.getEndOfReadCount() > 0) {
			
			if (pel.getMiddleOfReadCount() >= 5 && pel.isFoundOnBothStrandsMiddleOfRead()) {
				// all good
			} else {
				VcfUtils.updateFilter(rec.getVcfRecord(), SnpUtils.END_OF_READ + pel.getEndOfReadCount());
			}
		}
	}
	
	void checkForMutationInNormal() {
		int minCount = 0;
		for (QSnpRecord record : positionRecordMap.values()) {
			if (null != record.getAnnotation() && record.getAnnotation().contains(SnpUtils.MUTATION_IN_NORMAL)) {
				// check to see if mutant count in normal is 3% or more
				// if not, remove annotation
				final String ND = record.getNormalNucleotides();
				final int normalCount = record.getNormalCount();
				String alt = SnpUtils.getAltFromMutationString(record.getMutation());
				final int altCount = SnpUtils.getCountFromNucleotideString(ND, alt);
				
				if (((float)altCount / normalCount) * 100 < 3.0f) {
					VcfUtils.removeFilter(record.getVcfRecord(), SnpUtils.MUTATION_IN_NORMAL);
					minCount++;
				}
			}
		}
		logger.info("no of records with " + SnpUtils.MUTATION_IN_NORMAL + " annotation removed: " + minCount);
	}
	
	private void interrogateAccumulations(final Accumulator normal, final Accumulator tumour) {
		
		// get coverage for both normal and tumour
		final int normalCoverage = null != normal ? normal.getCoverage() : 0;
		final int tumourCoverage = null != tumour ? tumour.getCoverage() : 0;
		
		if (normalCoverage + tumourCoverage < initialTestSumOfCountsLimit) return;
		
		final int position = normal != null ? normal.getPosition() : tumour.getPosition();
		
		// if we are over the length of this particular sequence - return
		if (position-1 >= referenceBasesLength) return;
		
		char ref = (char) referenceBases[position-1];
		if (Character.isLowerCase(ref)) ref = Character.toUpperCase(ref);
		
		// get rule for normal and tumour
		final Rule normalRule = RulesUtil.getRule(controlRules, normalCoverage);
		final Rule tumourRule = RulesUtil.getRule(testRules, tumourCoverage);
		
		
		boolean [] normalPass = PileupElementLiteUtil.isAccumulatorAKeeper(normal,  ref, normalRule, baseQualityPercentage);
		boolean [] tumourPass = PileupElementLiteUtil.isAccumulatorAKeeper(tumour,  ref, tumourRule, baseQualityPercentage);
		
		
		if (normalPass[0] || normalPass[1] || tumourPass[0] || tumourPass[1]) {
		
			String normalBases = null != normal ? normal.toSamtoolsPileupString(ref) : "";
			String tumourBases = null != tumour ? tumour.toSamtoolsPileupString(ref) : "";
			QSnpRecord qRecord = new QSnpRecord(currentChr, position, ref+"");
			qRecord.setPileup((null != normal ? normal.toPileupString(normalBases) : "") 
					+ "\t" + (null != tumour ? tumour.toPileupString(tumourBases) : ""));
			// setup some values on the record
//			qRecord.setRef(ref);
			qRecord.setNormalCount(normalCoverage);
			qRecord.setTumourCount(tumourCoverage);
			
			// set normal pileup to only contain the different bases found in normal, rather than the whole pileup string
			// which contains special chars indicating start/end of reads along with mapping qualities
			if (normalCoverage > 0)
				qRecord.setNormalPileup(normal.getCompressedPileup());
			// use all base counts to form genotype
			qRecord.setNormalGenotype(null != normal ? normal.getGenotype(ref, normalRule, normalPass[1], baseQualityPercentage) : null);
			qRecord.setTumourGenotype(null != tumour ? tumour.getGenotype(ref, tumourRule, tumourPass[1], baseQualityPercentage) : null);
			
			qRecord.setNormalNucleotides(null != normal ? normal.getPileupElementString() : null);
			qRecord.setTumourNucleotides(null != tumour ? tumour.getPileupElementString() : null);
			// add unfiltered normal
			if (null != normal)
				qRecord.setUnfilteredNormalPileup(normal.getUnfilteredPileup());
			
			
			qRecord.setNormalNovelStartCount(PileupElementLiteUtil.getLargestVariantNovelStarts(normal, ref));
			qRecord.setTumourNovelStartCount(PileupElementLiteUtil.getLargestVariantNovelStarts(tumour, ref));
			
			classifyPileupRecord(qRecord);
			
			if (null != qRecord.getMutation()) {
				String altString =SnpUtils.getAltFromMutationString(qRecord.getMutation());
				if (altString.length() > 1) {
					logger.warn("altString: " + altString + " in Pipeline.interrogateAccumulations");
				}
				char alt = altString.charAt(0);
				
				// get and set cpg
				final char[] cpgCharArray = new char[11];
				for (int i = 0 ; i < 11 ; i++) {
					int refPosition = position - (6 - i);
					if (i == 5) {
						cpgCharArray[i] = alt;
					} else if ( refPosition >= 0 && refPosition < referenceBasesLength) {
						cpgCharArray[i] = Character.toUpperCase((char) referenceBases[refPosition]);
					} else {
						cpgCharArray[i] = '-';
					}
				}
				qRecord.setFlankingSequence(String.valueOf(cpgCharArray));
				
				// only do fisher for SOMATICS
				if (Classification.SOMATIC == qRecord.getClassification()) {
					// fisher test work here - we need an alt to do this
					int normalAlt = null != normal ? normal.getBaseCountForBase(alt) : 0;		// a
					int normalRef = null != normal ? normal.getBaseCountForBase(ref) : 0;	// c
					int tumourAlt = null != tumour ? tumour.getBaseCountForBase(alt) : 0;		// b
					int tumourRef = null != tumour ? tumour.getBaseCountForBase(ref) : 0;	// d
					
					
					// don't run this if we have crazy large coverage - takes too long and holds up the other threads
					if (normalCoverage + tumourCoverage > 100000) {
						logger.info("skipping FisherExact pValue calculation - coverage too large: " + normalCoverage + ", tCov: " + tumourCoverage + ", at " + currentChr + ":" + position);
					} else {
						double pValue = FisherExact.getTwoTailedFETMath(normalAlt, tumourAlt, normalRef, tumourRef);
	//					if (normalAlt > 1000 || normalRef > 1000 || tumourAlt > 1000 || tumourRef > 1000) {
	//						logger.info("pValue: " + pValue );
	//					}
		//				logger.info("pvalue for following values (a,b,c,d:pvalue): " + normalAlt + "," + tumourAlt + "," + normalRef + "," + tumourRef + ": " + pValue);
						qRecord.setProbability(pValue);
						if (++pValueCount % 1000 == 0) logger.info("hit " + pValueCount + " pValue calculations");
					}
				}
				
				// strand bias check
				checkForStrandBias(qRecord, normal, tumour, ref);
				
				// ends of read check
				checkForEndsOfReads(qRecord, normal, tumour, ref);
			}
			
			// set Id
			qRecord.setId(++mutationId);
			logger.debug("adding: " + qRecord.getDCCDataNSFlankingSeq(null, null));
			positionRecordMap.put(qRecord.getChrPos(), qRecord);
			accumulators.put(qRecord.getChrPos(), new Pair<Accumulator, Accumulator>(normal, tumour));
		}
	}
	
	
	void compoundSnps() {
		logger.info("in compound snp");
		// loop through our snps
		// if we have adjacent ones, need to do some digging to see if they might be compoundium snps
		
		int size = positionRecordMap.size();
		
		
		
		List<ChrPosition> keys = new ArrayList<>(positionRecordMap.keySet());
		Collections.sort(keys);
		
		int noOfCompSnpsSOM = 0;
		int noOfCompSnpsGERM = 0;
		int noOfCompSnpsMIXED = 0;
		int [] compSnpSize = new int[10000];
		Arrays.fill(compSnpSize, 0);
 		
		for (int i = 0 ; i < size-1 ; ) {
			
			ChrPosition thisPosition = keys.get(i++);
			ChrPosition nextPosition = keys.get(i);
			
			if (ChrPositionUtils.areAdjacent(thisPosition, nextPosition)) {
				
				// check to see if they have the same classification
				QSnpRecord qsnpr1 = positionRecordMap.get(thisPosition);
				QSnpRecord qsnpr2 = positionRecordMap.get(nextPosition);
				
				if (qsnpr1.getClassification() != qsnpr2.getClassification() ) {
					continue;
				}
				
				ChrPosition start = thisPosition;		// starting point of compound snp
				final int startPosition= start.getPosition();
				
				while (i < size-1) {
					thisPosition = nextPosition;
					nextPosition = keys.get(++i);		// end point of compound snp
					if ( ! ChrPositionUtils.areAdjacent(thisPosition, nextPosition)) {
						break;
					}
					// check to see if they have the same classification
					qsnpr1 = positionRecordMap.get(thisPosition);
					qsnpr2 = positionRecordMap.get(nextPosition);
					
					if (qsnpr1.getClassification() != qsnpr2.getClassification() ) {
						break;
					}
				}
				
				if (start.equals(thisPosition)) {
					// set thisPosition to be nextPosition
					thisPosition = nextPosition;
				}
				
				// thisPosition should now be the end of the compound snp
				
				final int endPosition = thisPosition.getPosition();
				// how many bases does our compound snp cover?
				int noOfBases = (endPosition - startPosition) + 1;
				
				// create new ChrPosition object that spans the region we are interested and create a list with the QSnpRecords
				
				ChrPosition csChrPos = new ChrPosition(start.getChromosome(), startPosition, endPosition);
				List<QSnpRecord> csRecords = new ArrayList<>();
				String ref = "", alt = "", classification = "", flag = "";
				for (int k = 0 ; k < csChrPos.getLength() ; k++) {
					QSnpRecord rec = positionRecordMap.get(new ChrPosition(start.getChromosome(), startPosition + k));
					csRecords.add(rec);
					ref += rec.getRef();
//					if (alt.length() > 0) {
//						alt += ",";
//					}
					if (null != rec.getMutation()) {
						alt += rec.getMutation().charAt(rec.getMutation().length() -1);
					}
					if (classification.length() > 0) {
						classification += ",";
					}
					classification += rec.getClassification();
					if (flag.length() > 0) {
						flag += Constants.SEMI_COLON;
					}
					flag += rec.getAnnotation();
				}
				
				// get unique list of flags
				String [] flagArray = flag.split(""+Constants.SEMI_COLON);
				Set<String> uniqueFlags = new HashSet<>(Arrays.asList(flagArray));
				
				StringBuilder uniqueFlagsSb = new StringBuilder();
				for (String s : uniqueFlags) {
					if (uniqueFlagsSb.length() > 0) {
						uniqueFlagsSb.append(Constants.SEMI_COLON);
					}
					uniqueFlagsSb.append(s);
				}
				
				
				Map<Long, StringBuilder> normalReadSeqMap = new HashMap<>();
				Map<Long, StringBuilder> tumourReadSeqMap = new HashMap<>();
				
				// get accumulation objects for this position
				for (int j = startPosition ; j <= endPosition ; j++) {
					ChrPosition cp = new ChrPosition(csChrPos.getChromosome(), j);
					Pair<Accumulator, Accumulator> accums = accumulators.get(cp);
					Accumulator normal = accums.getLeft();
					Accumulator tumour = accums.getRight();
					if (null != normal) {
						accumulateReadBases(normal, normalReadSeqMap, (j - startPosition));
					}
					if (null != tumour) {
						accumulateReadBases(tumour, tumourReadSeqMap, (j - startPosition));
					}
				}
				
				Map<String, AtomicInteger> normalMutationCount = new HashMap<>();
				for (Entry<Long, StringBuilder> entry : normalReadSeqMap.entrySet()) {
					AtomicInteger count = normalMutationCount.get(entry.getValue().toString());
					if (null == count) {
						normalMutationCount.put(entry.getValue().toString(), new AtomicInteger(1));
					} else {
						count.incrementAndGet();
					}
				}
				Map<String, AtomicInteger> tumourMutationCount = new HashMap<>();
				for (Entry<Long, StringBuilder> entry : tumourReadSeqMap.entrySet()) {
					AtomicInteger count = tumourMutationCount.get(entry.getValue().toString());
					if (null == count) {
						tumourMutationCount.put(entry.getValue().toString(), new AtomicInteger(1));
					} else {
						count.incrementAndGet();
					}
				}
				
				AtomicInteger normalAltCountFS = normalMutationCount.get(alt);
				AtomicInteger tumourAltCountFS = tumourMutationCount.get(alt);
				AtomicInteger normalAltCountRS = normalMutationCount.get(alt.toLowerCase());
				AtomicInteger tumourAltCountRS = tumourMutationCount.get(alt.toLowerCase());
				int nc = (null != normalAltCountFS ? normalAltCountFS.get() : 0) + (null != normalAltCountRS ? normalAltCountRS.get() : 0) ;
				int tc = (null != tumourAltCountFS ? tumourAltCountFS.get() : 0) + (null != tumourAltCountRS ? tumourAltCountRS.get() : 0);
				
				int totalAltCount = nc + tc;
				boolean somatic = classification.contains(Classification.SOMATIC.toString());
				boolean germline = classification.contains(Classification.GERMLINE.toString());
				// don't care about UNKNOWNs at the moment...
				
				if (totalAltCount >= 4 && ! (somatic && germline)) {
					logger.info("will create CS, totalAltCount: " + totalAltCount + " classification: " + classification + " : " + csChrPos.toString());
					
					
					StringBuilder normalSb = new StringBuilder();
					List<String> bases = new ArrayList<>(normalMutationCount.keySet());
					Collections.sort(bases);
					
					for (String key : bases) {
						String upperCaseKey = key.toUpperCase();
						if (normalSb.indexOf(upperCaseKey) == -1) {
							if (normalSb.length() > 0) {
								normalSb.append(",");
							}
							AtomicInteger fsAI = normalMutationCount.get(upperCaseKey);
							AtomicInteger rsAI = normalMutationCount.get(upperCaseKey.toLowerCase());
							int fs = null != fsAI ? fsAI.get() : 0;
							int rs = null != rsAI ? rsAI.get() : 0;
							normalSb.append(upperCaseKey + "," + fs + "," + rs);
						}
					}
					StringBuilder tumourSb = new StringBuilder();
					bases = new ArrayList<>(tumourMutationCount.keySet());
					Collections.sort(bases);
					for (String key : bases) {
						String upperCaseKey = key.toUpperCase();
						if (tumourSb.indexOf(upperCaseKey) == -1) {
							if (tumourSb.length() > 0) {
								tumourSb.append(",");
							}
							AtomicInteger fsAI = tumourMutationCount.get(upperCaseKey);
							AtomicInteger rsAI = tumourMutationCount.get(upperCaseKey.toLowerCase());
							int fs = null != fsAI ? fsAI.get() : 0;
							int rs = null != rsAI ? rsAI.get() : 0;
							tumourSb.append(upperCaseKey + "," + fs + "," + rs);
						}
					}
					
					logger.info(csChrPos.toString() + " - ref bases: " + ref + ", alt: " + alt + " : " + classification + ", flag: " + flag + "\nnormal: " + normalSb.toString() + "\ntumour: " + tumourSb.toString());
					
					// create new VCFRecord with start position
					VcfRecord cs = VcfUtils.createVcfRecord(csChrPos, null, ref, alt);
					if (somatic) {
						cs.addInfo(Classification.SOMATIC.toString());
					}
					cs.setFilter(uniqueFlagsSb.toString());		// unique list of filters seen by snps making up this cs
					cs.setFormatField(Arrays.asList(VcfUtils.FORMAT_ALLELE_COUNT_COMPOUND_SNP
							, normalSb.toString(), tumourSb.toString())); 
					
					compoundSnps.put(csChrPos, cs);
					
					// remove old records from map, and add new one
					for (QSnpRecord rec : csRecords) {
						positionRecordMap.remove(rec.getChrPos());
					}
					compSnpSize[noOfBases] ++;
				} else {
					logger.info("won't create CS, totalAltCount: " + totalAltCount + " classification: " + classification + " : " + csChrPos.toString());
				}
			}
		}
		
		logger.info("found " + (noOfCompSnpsSOM +noOfCompSnpsGERM + noOfCompSnpsMIXED)  + " compound snps - no in map: " + compoundSnps.size());
		logger.info("noOfCompSnpsSOM: " + noOfCompSnpsSOM + ", noOfCompSnpsGERM: " + noOfCompSnpsGERM +", noOfCompSnpsMIXED: " + noOfCompSnpsMIXED);
		
		for (int i = 0 ; i < compSnpSize.length ; i++) {
			if (compSnpSize[i] > 0) {
				logger.info("no of compound snps of length: " + i + " : " + compSnpSize[i]);
			}
		}
	}
	
	void accumulateReadBases(Accumulator acc, Map<Long, StringBuilder> readSeqMap, int position) {
		Map<Long, Character> map = acc.getReadIdBaseMap();
		// get existing seq for each id
		for (Entry<Long, Character> entry : map.entrySet()) {
			StringBuilder seq = readSeqMap.get(entry.getKey());
			if (null == seq) {
				// initialise based on how far away we are from the start
				seq = new StringBuilder();
				for (int q = (position) ; q > 0 ; q--) {
					seq.append("_");
				}
				readSeqMap.put(entry.getKey(), seq);
			}
			seq.append(entry.getValue());
		}
		
		// need to check that all elements have enough data in them
		for (StringBuilder sb : readSeqMap.values()) {
			if (sb.length() <= position) {
				sb.append("_");
			}
		}
	}

	protected void strandBiasCorrection() {
			// remove SBIAS flag should there be no reads at all on the opposite strand
			int noOfSnpsWithSBIAS = 0, removed = 0;
			for (QSnpRecord record : positionRecordMap.values()) {
				if (record.getAnnotation().contains(SnpUtils.STRAND_BIAS)) {
					noOfSnpsWithSBIAS++;
					
					// check to see if we have any reads at all on the opposite strand
					// just checking the germline for the moment as we are currently in single file mode
					String ND = record.getNormalNucleotides();
	//				logger.info("sending ND to check for strand bias: " + ND);
					boolean onBothStrands = SnpUtils.doesNucleotideStringContainReadsOnBothStrands(ND);
					if ( ! onBothStrands) {
	//					logger.info("removing SBIAS annotation for ND: " + ND);
						VcfUtils.removeFilter(record.getVcfRecord(), SnpUtils.STRAND_BIAS);
						removed++;
					}
				}
			}
			logger.info("no of snps with SBIAS: " + noOfSnpsWithSBIAS + ", and no removed: " + removed);
			
		}
	
}
