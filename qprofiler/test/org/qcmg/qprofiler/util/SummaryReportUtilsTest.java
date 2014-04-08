package org.qcmg.qprofiler.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import junit.framework.Assert;
import net.sf.samtools.SAMRecord;

import org.junit.Test;
import org.qcmg.common.model.QCMGAtomicLongArray;
import org.qcmg.common.model.SummaryByCycle;
import org.qcmg.common.model.SummaryByCycleNew2;
import org.qcmg.qprofiler.bam.PositionSummary;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class SummaryReportUtilsTest {
	@Test
	public void testTallyBadReads() {
		ConcurrentMap<Integer, AtomicLong> badReadCount = new ConcurrentHashMap<Integer, AtomicLong>();
		
		// empty string
		String badRead = "";
		SummaryReportUtils.tallyBadReads(badRead, badReadCount);
		Assert.assertNotNull(badReadCount);
		Assert.assertEquals(1, badReadCount.get(Integer.valueOf(0)).get());
		
		badRead = "1234";
		SummaryReportUtils.tallyBadReads(badRead, badReadCount);
		Assert.assertEquals((2), badReadCount.get(Integer.valueOf(0)).get());
		
		badRead = "1.34";
		SummaryReportUtils.tallyBadReads(badRead, badReadCount);
		Assert.assertEquals((2), badReadCount.get(Integer.valueOf(0)).get());
		Assert.assertEquals((1), badReadCount.get(Integer.valueOf(1)).get());
		
		badRead = ".234";
		SummaryReportUtils.tallyBadReads(badRead, badReadCount);
		Assert.assertEquals((2), badReadCount.get(Integer.valueOf(0)).get());
		Assert.assertEquals((2), badReadCount.get(Integer.valueOf(1)).get());
		
		badRead = ".23.";
		SummaryReportUtils.tallyBadReads(badRead, badReadCount);
		Assert.assertEquals((1), badReadCount.get(Integer.valueOf(2)).get());
		
		badRead = "N23.";
		SummaryReportUtils.tallyBadReads(badRead, badReadCount);
		Assert.assertEquals((2), badReadCount.get(Integer.valueOf(2)).get());
		
		badRead = "N...N.NNN.";
		SummaryReportUtils.tallyBadReads(badRead, badReadCount);
		Assert.assertEquals((1), badReadCount.get(Integer.valueOf(10)).get());
		
		badRead = "N.";
		SummaryReportUtils.tallyBadReads(badRead, badReadCount);
		Assert.assertEquals((3), badReadCount.get(Integer.valueOf(2)).get());
		
		badRead = "N";
		SummaryReportUtils.tallyBadReads(badRead, badReadCount);
		Assert.assertEquals((3), badReadCount.get(Integer.valueOf(1)).get());

		
		badRead = "....1....1....1";
		SummaryReportUtils.tallyBadReads(badRead, badReadCount);
		Assert.assertEquals((1), badReadCount.get(Integer.valueOf(12)).get());
		
		// null string
		SummaryReportUtils.tallyBadReads(null, badReadCount);
		
		// null map
		SummaryReportUtils.tallyBadReads(null, null);
		
		// null map
		try {
			SummaryReportUtils.tallyBadReads("anything in here", null);
			Assert.fail("Should have thrown an AssertionError");
		} catch (AssertionError ae) {
			Assert.assertTrue(ae.getMessage().startsWith("Null map"));
		}
	}
	
	@Test
	public void testMatcherVsByteArray() {
		String inputString = "12345.12345.12345.12345...12345";
		int counter = 100000;
		
		ConcurrentMap<Integer, AtomicLong> map = new ConcurrentHashMap<Integer, AtomicLong>();
		
		long start = System.currentTimeMillis();
		for (int i = 0 ; i < counter ; i++) {
			SummaryReportUtils.tallyBadReads(inputString, map);
		}
		System.out.println("time taken using Matcher: " + (System.currentTimeMillis() - start));
		Assert.assertEquals(counter, map.get(6).get());
		
//		map = new HashMap<Integer, AtomicLong>();
//		start = System.currentTimeMillis();
//		for (int i = 0 ; i < counter ; i++) {
//			SummaryReportUtils.tallyBadReadsAsByteArray(inputString, map);
//		}
//		System.out.println("time taken using String converted to byte[]: " + (System.currentTimeMillis() - start));
//		Assert.assertEquals(counter, map.get(6).get());
		
		map = new ConcurrentHashMap<Integer, AtomicLong>();
		start = System.currentTimeMillis();
		for (int i = 0 ; i < counter ; i++) {
			SummaryReportUtils.tallyBadReadsAsString(inputString, map);
		}
		System.out.println("time taken using String: " + (System.currentTimeMillis() - start));
		Assert.assertEquals(counter, map.get(6).get());
		
//		map = new HashMap<Integer, AtomicLong>();
//		start = System.currentTimeMillis();
//		byte [] bytes = inputString.getBytes();
//		for (int i = 0 ; i < counter ; i++) {
//			SummaryReportUtils.tallyBadReadsByteArray(bytes, map);
//		}
//		System.out.println("time taken byte array: " + (System.currentTimeMillis() - start));
//		Assert.assertEquals(counter, map.get(6).get());
		
		map = new ConcurrentHashMap<Integer, AtomicLong>();
		start = System.currentTimeMillis();
		for (int i = 0 ; i < counter ; i++) {
			SummaryReportUtils.tallyBadReads(inputString, map);
		}
		System.out.println("time taken using Matcher: " + (System.currentTimeMillis() - start));
		Assert.assertEquals(counter, map.get(6).get());
	}
	@Test
	public void testMatcherVsString() {
		String inputString = "ACGT1NNNN1TCGA1";
		long counter = 100000;
		
		ConcurrentMap<Integer, AtomicLong> map = new ConcurrentHashMap<Integer, AtomicLong>();
		ConcurrentMap<String, AtomicLong> mapMD = new ConcurrentHashMap<String, AtomicLong>();
		
		long start = System.currentTimeMillis();
		for (int i = 0 ; i < counter ; i++) {
			SummaryReportUtils.tallyBadReads(inputString, map, SummaryReportUtils.BAD_MD_PATTERN);
		}
		System.out.println("testMatcherVsString: time taken using Matcher: " + (System.currentTimeMillis() - start));
		Assert.assertEquals(counter, map.get(12).get());
		
		start = System.currentTimeMillis();
		for (int i = 0 ; i < counter ; i++) {
			SummaryReportUtils.tallyBadReadsMD(inputString, mapMD);
		}
		System.out.println("testMatcherVsString: time taken using String looping: " + (System.currentTimeMillis() - start));
		Assert.assertEquals(counter * 3, mapMD.get("4M").get());
		
		start = System.currentTimeMillis();
		map = new ConcurrentHashMap<Integer, AtomicLong>();
		for (int i = 0 ; i < counter ; i++) {
			SummaryReportUtils.tallyBadReads(inputString, map, SummaryReportUtils.BAD_MD_PATTERN);
		}
		System.out.println("testMatcherVsString: time taken using Matcher: " + (System.currentTimeMillis() - start));
		Assert.assertEquals(counter, map.get(12).get());
		
	}
	
	@Test
	public void testTallyMDMismatches() {
		SummaryByCycle<Character> summary = new SummaryByCycle<Character>();
		String mdString = "50";
		String readBases = "AAAAAAAAAACCCCCCCCCCGGGGGGGGGGTTTTTTTTTTAAAAAAAAAAT";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases);
		Assert.assertTrue(summary.cycles().isEmpty());
		
		mdString = "20A4";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases);
		Assert.assertFalse(summary.cycles().isEmpty());
		Assert.assertEquals(1, summary.count(21, 'G').intValue());

		// update tests...
		mdString = "14C35";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases);
		Assert.assertEquals(1, summary.count(15, 'C').intValue());
		mdString = "0N49";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases);
		Assert.assertEquals(1, summary.count(1, 'A').intValue());
		mdString = "21G28";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases);
		Assert.assertEquals(1, summary.count(22, 'G').intValue());
		mdString = "13T0G32";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases);
		Assert.assertEquals(1, summary.count(14, 'C').intValue());
		Assert.assertEquals(2, summary.count(15, 'C').intValue());
		mdString = "5A0G43";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases);
		Assert.assertEquals(1, summary.count(6, 'A').intValue());
		Assert.assertEquals(1, summary.count(7, 'A').intValue());
		mdString = "16G2G30";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases);
		Assert.assertEquals(1, summary.count(17, 'C').intValue());
		Assert.assertEquals(1, summary.count(20, 'C').intValue());
		mdString = "20^G5";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases);
		Assert.assertEquals(1, summary.count(21, 'G').intValue());	// won't have increased the count - deletion
		mdString = "17^TTCCAGCTG7A0";	// CIGAR: 17M9D8M, seq: AGAGTGAGAATCTGTTGATGACTCN
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases);
		Assert.assertEquals(1, summary.count(25, 'G').intValue());
		mdString = "17^TTT7T0";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases);
		Assert.assertEquals(2, summary.count(25, 'G').intValue());
		mdString = "3^GTTGAC22";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases);
		Assert.assertEquals(1, summary.count(21, 'G').intValue());	// won't have increased the count - deletion
		mdString = "3A0T2^CCTGGATCAA18";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases);
		Assert.assertEquals(1, summary.count(4, 'A').intValue());
		Assert.assertEquals(1, summary.count(5, 'A').intValue());
		mdString = "39A6^TGCTGTGGCC4T0";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases);
		Assert.assertEquals(1, summary.count(40, 'T').intValue());
		Assert.assertEquals(1, summary.count(51, 'T').intValue());
		
		// and finally where the variant is the first character....
		mdString = "TC23";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases);
		Assert.assertEquals(2, summary.count(1, 'A').intValue());
		Assert.assertEquals(1, summary.count(2, 'A').intValue());
		
		mdString = "T24G";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases);
		Assert.assertEquals(3, summary.count(1, 'A').intValue());
		Assert.assertEquals(1, summary.count(26, 'G').intValue());
		
		//same again with 0 in front of the variation
		mdString = "0T0C23";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases);
		Assert.assertEquals(4, summary.count(1, 'A').intValue());
		Assert.assertEquals(2, summary.count(2, 'A').intValue());
		
		mdString = "0T24G";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases);
		Assert.assertEquals(5, summary.count(1, 'A').intValue());
		Assert.assertEquals(2, summary.count(26, 'G').intValue());
		
		
		// extra long mds.... taken from real life bam file
		mdString = "30^AGAAAATGTTTTTCATTTTCTTGATTTATTTCTGAATTCAGCTTGCTCTTCATTAGCGCTACATAGCTGMCTTATTATTCGTGGTC" +
				"CCCTATGACCCCCTGATCATTTTCCCTGAGGGTGCATATTTATTCACTAACTATGTTACAATCATGTGATCTGCTGGATTTTTTCTGATA" +
				"GTCTACTCTAGATTTGTTCTAAATTAATAAATCCCATTATTTTTGGCTTCTACTACTTCTATTTATTAAATTCATTCTGAATATGAAGTTTATTT" +
				"TCAAAGGAATTCATAATTCTTTACTCCRRGCTTGGTTCTAACAATGAATTTAATAAGAATTGTATTTAATCAATGTTTAAATATATTAAGGGC" +
				"AAATTTTGTAAAAATGTTAGTGTTCCAAGCTTTCCATTTCCCCACAAATTAATTTTTTTAGCCTTTCCCCTTAATCCACTTTCTT19G0";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases);
		Assert.assertEquals(1, summary.count(50, 'A').intValue());
		
		
		
		mdString = "17C2^CCACTTAATTTCATGTGATAATTTTCCCCAATGACTAACCAAATATGCTTCACTATTATATAAATCAATTCTTTCTTAATGCC" +
				"ACAAGTGAAAGTGCAAAGGTAGCTAATGGTTTTCTTCTCATAAAAATCACACTTTGGCTTTTTCCTTTCATATGTAATTAATCATATT" +
				"TGTGACAATCTTCCAAACTTACTTGAAATTTTTCTGAATCCCTTTCAAATCAGGACAAGAACTAGAAATGTCTATACAGGTTTAATAT" +
				"GAAGTAAAGAAAATGTTTTTCATTTTCTTGATTTATTTCTGAATTCAGCTTGCTCTTCATTAGCGCTACATAGCTGMCTTATTATTCG" +
				"TGGTCCCCTATGACCCCCTGATCATTTTCCCTGAGGGTGCATATTTATTCACTAACTATGTTACAATCATGTGATCTGCTGGATTTT" +
				"TTCTGATAGTCTACTCTAGATTTGTTCTAAATTAATAAATCCCATTATTTTTG30";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases);
		Assert.assertEquals(1, summary.count(18, 'C').intValue());
		
	}
	
	@Test
	public void testTallyMDMismatchesRealLifeData() {
		SummaryByCycleNew2<Character> summary = new SummaryByCycleNew2<Character>(Character.MAX_VALUE, 64);
		QCMGAtomicLongArray forwardArray = new QCMGAtomicLongArray(32);
		QCMGAtomicLongArray reverseArray = new QCMGAtomicLongArray(32);
		String readBasesString = "GCTCTCCGATCTAACCCTAACCCTAACCCTAACCCTAACCCTAACCCTAACCCTAACCCTAACCCTAACCCTAACCCTAACCCTAACCCTAACCCTAACCC";
		byte[] readBases = readBasesString.getBytes();
		SummaryReportUtils.tallyMDMismatches("0T2G95", summary, readBases, false, forwardArray, reverseArray);
		
		readBasesString = "CCCTAACCCTAACCCTAACCCTAACCCTAACCCTAACCCTAACCCTAACCCTAACCCTAACCCTAACCCTAACCCTAACCCTAACACTAAGATCGGAAGAG";
		readBases = readBasesString.getBytes();
		SummaryReportUtils.tallyMDMismatches("3C4C1T4A4", summary, readBases, false, forwardArray, reverseArray);
	}
	
	@Test
	public void testTallyMDMismatchesSecondaryAlignment() {
		SummaryByCycleNew2<Character> summary = new SummaryByCycleNew2<Character>(Character.MAX_VALUE, 64);
		QCMGAtomicLongArray forwardArray = new QCMGAtomicLongArray(32);
		QCMGAtomicLongArray reverseArray = new QCMGAtomicLongArray(32);
		SAMRecord sam = new SAMRecord(null);
		sam.setReadString("*");
		SummaryReportUtils.tallyMDMismatches("0N0N0N0T0A0A1C0C0T0A0A0C0C0C0T0A0A0C0C0C0T0A0A0C1C0T0A0A0C1C0T0A1C0C0C1A0A0C0C0C1A0A0C0C0C0T0A0A0C0C45", summary, sam.getReadBases(), false, forwardArray, reverseArray);
		
		Assert.assertEquals(0, summary.cycles().size());
		for (int i = 0 ; i < forwardArray.length() ; i++) {
			Assert.assertEquals(0,  forwardArray.get(i));
		}
		for (int i = 0 ; i < reverseArray.length() ; i++) {
			Assert.assertEquals(0,  reverseArray.get(i));
		}
		
		SummaryReportUtils.tallyMDMismatches("0C0C0C0T0A0A1C0C0T0A0A0C0C0C0T0A0A0C0C0C0T0A0A0C1C0T0A0A0C1C0T0A1C0C0C1A0A0C0C0C1A0A0C0C0C0T0A0A0C0C8A0A0C0C0C0T0A1C0C0C1A0A0C1C0T0A1C0C0", summary, sam.getReadBases(), false, forwardArray, reverseArray);
		
		Assert.assertEquals(0, summary.cycles().size());
		for (int i = 0 ; i < forwardArray.length() ; i++) {
			Assert.assertEquals(0,  forwardArray.get(i));
		}
		for (int i = 0 ; i < reverseArray.length() ; i++) {
			Assert.assertEquals(0,  reverseArray.get(i));
		}
	}
	
	@Test
	public void testTallyMDMismatchesWithStrand() {
		SummaryByCycleNew2<Character> summary = new SummaryByCycleNew2<Character>(Character.MAX_VALUE, 64);
		QCMGAtomicLongArray mdRefAltLengthsForward = new QCMGAtomicLongArray(32);
		QCMGAtomicLongArray mdRefAltLengthsReverse = new QCMGAtomicLongArray(32);
		String mdString = "51";
		final String readBasesString = "AAAAAAAAAACCCCCCCCCCGGGGGGGGGGTTTTTTTTTTAAAAAAAAAAT";
		final byte[] readBases = readBasesString.getBytes();
		final int readLength = readBases.length;
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, true, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertTrue(summary.cycles().isEmpty());
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, false, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertTrue(summary.cycles().isEmpty());
		
		mdString = "20A30";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, true, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertFalse(summary.cycles().isEmpty());
		Assert.assertEquals(1, summary.count(31, 'C').intValue());
		Assert.assertEquals(1, mdRefAltLengthsReverse.get(SummaryReportUtils.getIntFromChars('T', 'C')));
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, false, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertFalse(summary.cycles().isEmpty());
		Assert.assertEquals(1, summary.count(21, 'G').intValue());
		Assert.assertEquals(1, mdRefAltLengthsForward.get(SummaryReportUtils.getIntFromChars('A', 'G')));
		
		// update tests...
		mdString = "14C35";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, false, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(15, 'C').intValue());
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, true, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(readLength - 15 + 1, 'G').intValue());
		mdString = "0N49";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, false, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(1, 'A').intValue());
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, true, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(readLength - 1 + 1, 'T').intValue());
		mdString = "21G28";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, false, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(22, 'G').intValue());
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, true, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(readLength - 22 + 1, 'C').intValue());
		mdString = "13T0G32";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, false, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(14, 'C').intValue());
		Assert.assertEquals(2, summary.count(15, 'C').intValue());
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, true, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(readLength - 14 + 1, 'G').intValue());
		Assert.assertEquals(2, summary.count(readLength - 15 + 1, 'G').intValue());
		mdString = "5A0G43";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, false, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(6, 'A').intValue());
		Assert.assertEquals(1, summary.count(7, 'A').intValue());
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, true, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(readLength - 6 + 1, 'T').intValue());
		Assert.assertEquals(1, summary.count(readLength - 7 + 1, 'T').intValue());
		mdString = "16G2G30";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, false, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(17, 'C').intValue());
		Assert.assertEquals(1, summary.count(20, 'C').intValue());
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, true, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(readLength - 17 + 1, 'G').intValue());
		Assert.assertEquals(1, summary.count(readLength - 20 + 1, 'G').intValue());
		mdString = "20^G5";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, false, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(21, 'G').intValue());	// won't have increased the count - deletion
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, true, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(readLength - 21 + 1, 'C').intValue());	// won't have increased the count - deletion
		mdString = "17^TTCCAGCTG7A0";	// CIGAR: 17M9D8M, seq: AGAGTGAGAATCTGTTGATGACTCN
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, false, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(25, 'G').intValue());
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, true, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(readLength - 25 + 1, 'C').intValue());
		mdString = "17^TTT7T0";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, false, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(2, summary.count(25, 'G').intValue());
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, true, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(2, summary.count(readLength - 25 + 1, 'C').intValue());
		mdString = "3^GTTGAC22";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, false, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(21, 'G').intValue());	// won't have increased the count - deletion
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, true, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(readLength - 21 + 1, 'C').intValue());	// won't have increased the count - deletion
		mdString = "3A0T2^CCTGGATCAA18";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, false, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(4, 'A').intValue());
		Assert.assertEquals(1, summary.count(5, 'A').intValue());
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, true, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(readLength - 4 + 1, 'T').intValue());
		Assert.assertEquals(1, summary.count(readLength - 5 + 1, 'T').intValue());
		mdString = "39A6^TGCTGTGGCC4T0";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, false, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(40, 'T').intValue());
		Assert.assertEquals(2, summary.count(51, 'T').intValue());
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, true, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(readLength - 40 + 1, 'A').intValue());
		Assert.assertEquals(2, summary.count(readLength - 51 + 1, 'A').intValue());
		
		// and finally where the variant is the first character....
		mdString = "TC23";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, false, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(3, summary.count(1, 'A').intValue());
		Assert.assertEquals(1, summary.count(2, 'A').intValue());
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, true, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(3, summary.count(readLength - 1 + 1, 'T').intValue());
		Assert.assertEquals(1, summary.count(readLength - 2 + 1, 'T').intValue());
		
		mdString = "T24G";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, false, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(4, summary.count(1, 'A').intValue());
		Assert.assertEquals(1, summary.count(26, 'G').intValue());
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, true, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(4, summary.count(readLength - 1 + 1, 'T').intValue());
		Assert.assertEquals(1, summary.count(readLength - 26 + 1, 'C').intValue());
		
		//same again with 0 in front of the variation
		mdString = "0T0C23";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, false, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(5, summary.count(1, 'A').intValue());
		Assert.assertEquals(2, summary.count(2, 'A').intValue());
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, true, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(5, summary.count(readLength - 1 + 1, 'T').intValue());
		Assert.assertEquals(2, summary.count(readLength - 2 + 1, 'T').intValue());
		
		mdString = "0T24G";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, false, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(6, summary.count(1, 'A').intValue());
		Assert.assertEquals(2, summary.count(26, 'G').intValue());
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, true, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(6, summary.count(readLength - 1 + 1, 'T').intValue());
		Assert.assertEquals(2, summary.count(readLength - 26 + 1, 'C').intValue());
		
		
		// extra long mds.... taken from real life bam file
		mdString = "30^AGAAAATGTTTTTCATTTTCTTGATTTATTTCTGAATTCAGCTTGCTCTTCATTAGCGCTACATAGCTGMCTTATTATTCGTGGTC" +
		"CCCTATGACCCCCTGATCATTTTCCCTGAGGGTGCATATTTATTCACTAACTATGTTACAATCATGTGATCTGCTGGATTTTTTCTGATA" +
		"GTCTACTCTAGATTTGTTCTAAATTAATAAATCCCATTATTTTTGGCTTCTACTACTTCTATTTATTAAATTCATTCTGAATATGAAGTTTATTT" +
		"TCAAAGGAATTCATAATTCTTTACTCCRRGCTTGGTTCTAACAATGAATTTAATAAGAATTGTATTTAATCAATGTTTAAATATATTAAGGGC" +
		"AAATTTTGTAAAAATGTTAGTGTTCCAAGCTTTCCATTTCCCCACAAATTAATTTTTTTAGCCTTTCCCCTTAATCCACTTTCTT19G0";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, false, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(50, 'A').intValue());
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, true, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(readLength - 50 + 1, 'T').intValue());
		
		
		
		mdString = "17C2^CCACTTAATTTCATGTGATAATTTTCCCCAATGACTAACCAAATATGCTTCACTATTATATAAATCAATTCTTTCTTAATGCC" +
		"ACAAGTGAAAGTGCAAAGGTAGCTAATGGTTTTCTTCTCATAAAAATCACACTTTGGCTTTTTCCTTTCATATGTAATTAATCATATT" +
		"TGTGACAATCTTCCAAACTTACTTGAAATTTTTCTGAATCCCTTTCAAATCAGGACAAGAACTAGAAATGTCTATACAGGTTTAATAT" +
		"GAAGTAAAGAAAATGTTTTTCATTTTCTTGATTTATTTCTGAATTCAGCTTGCTCTTCATTAGCGCTACATAGCTGMCTTATTATTCG" +
		"TGGTCCCCTATGACCCCCTGATCATTTTCCCTGAGGGTGCATATTTATTCACTAACTATGTTACAATCATGTGATCTGCTGGATTTT" +
		"TTCTGATAGTCTACTCTAGATTTGTTCTAAATTAATAAATCCCATTATTTTTG30";
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, false, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(18, 'C').intValue());
		SummaryReportUtils.tallyMDMismatches(mdString, summary, readBases, true, mdRefAltLengthsForward, mdRefAltLengthsReverse);
		Assert.assertEquals(1, summary.count(readLength - 18 + 1, 'G').intValue());
		
	}
	
	@Test
	public void testTallyBadMDReads() {
		ConcurrentMap<Integer, AtomicLong> badMDReadCount = new ConcurrentHashMap<Integer, AtomicLong>();
		
		// empty string
		String badRead = "";
		SummaryReportUtils.tallyBadReads(badRead, badMDReadCount, SummaryReportUtils.BAD_MD_PATTERN);
		Assert.assertNotNull(badMDReadCount);
		Assert.assertEquals(1, badMDReadCount.get(Integer.valueOf(0)).get());
		
		badRead = "1234";
		SummaryReportUtils.tallyBadReads(badRead, badMDReadCount, SummaryReportUtils.BAD_MD_PATTERN);
		Assert.assertEquals((2), badMDReadCount.get(Integer.valueOf(0)).get());
		
		badRead = "1A34";
		SummaryReportUtils.tallyBadReads(badRead, badMDReadCount, SummaryReportUtils.BAD_MD_PATTERN);
		Assert.assertEquals(2, badMDReadCount.get(Integer.valueOf(0)).get());
		Assert.assertEquals(1, badMDReadCount.get(Integer.valueOf(1)).get());
		
		badRead = "C234";
		SummaryReportUtils.tallyBadReads(badRead, badMDReadCount, SummaryReportUtils.BAD_MD_PATTERN);
		Assert.assertEquals((2), badMDReadCount.get(Integer.valueOf(0)).get());
		Assert.assertEquals((2), badMDReadCount.get(Integer.valueOf(1)).get());
		
		badRead = "T23N";
		SummaryReportUtils.tallyBadReads(badRead, badMDReadCount, SummaryReportUtils.BAD_MD_PATTERN);
		Assert.assertEquals((1), badMDReadCount.get(Integer.valueOf(2)).get());
		
		badRead = "N23G";
		SummaryReportUtils.tallyBadReads(badRead, badMDReadCount, SummaryReportUtils.BAD_MD_PATTERN);
		Assert.assertEquals((2), badMDReadCount.get(Integer.valueOf(2)).get());
		
		badRead = "NATGNANNNC";
		SummaryReportUtils.tallyBadReads(badRead, badMDReadCount, SummaryReportUtils.BAD_MD_PATTERN);
		Assert.assertEquals((1), badMDReadCount.get(Integer.valueOf(10)).get());
		
		badRead = "NT";
		SummaryReportUtils.tallyBadReads(badRead, badMDReadCount, SummaryReportUtils.BAD_MD_PATTERN);
		Assert.assertEquals((3), badMDReadCount.get(Integer.valueOf(2)).get());
		
		badRead = "N";
		SummaryReportUtils.tallyBadReads(badRead, badMDReadCount, SummaryReportUtils.BAD_MD_PATTERN);
		Assert.assertEquals((3), badMDReadCount.get(Integer.valueOf(1)).get());
		
		
		badRead = "ACGT1NNNN1TCGA1";
		SummaryReportUtils.tallyBadReads(badRead, badMDReadCount, SummaryReportUtils.BAD_MD_PATTERN);
		Assert.assertEquals((1), badMDReadCount.get(Integer.valueOf(12)).get());
		
		// null string
		SummaryReportUtils.tallyBadReads(null, badMDReadCount, SummaryReportUtils.BAD_MD_PATTERN);
		
		// null map
		SummaryReportUtils.tallyBadReads(null, null, SummaryReportUtils.BAD_MD_PATTERN);
		
		// null map
		try {
			SummaryReportUtils.tallyBadReads("anything in here", null, SummaryReportUtils.BAD_MD_PATTERN);
			Assert.fail("Should have thrown an AssertionError");
		} catch (AssertionError ae) {
			Assert.assertTrue(ae.getMessage().startsWith("Null map "));
		}
	}
	
	@Test
	public void testTallyBadMDReadsNEW() {
		ConcurrentMap<String, AtomicLong> badMDReadCount = new ConcurrentHashMap<String, AtomicLong>();
		
		// empty string
		String badRead = "";
		SummaryReportUtils.tallyBadReadsMD(badRead, badMDReadCount);
		Assert.assertNotNull(badMDReadCount);
		Assert.assertTrue(badMDReadCount.isEmpty());
		
		badRead = "1234";
		SummaryReportUtils.tallyBadReadsMD(badRead, badMDReadCount);
		Assert.assertTrue(badMDReadCount.isEmpty());
		
		badRead = "1A34";
		SummaryReportUtils.tallyBadReadsMD(badRead, badMDReadCount);
		Assert.assertEquals(1, badMDReadCount.get("1M").get());
		
		badRead = "C234";
		SummaryReportUtils.tallyBadReadsMD(badRead, badMDReadCount);
		Assert.assertEquals(2, badMDReadCount.get("1M").get());
		
		badRead = "T23N";
		SummaryReportUtils.tallyBadReadsMD(badRead, badMDReadCount);
		Assert.assertEquals((4), badMDReadCount.get("1M").get());
		
		badRead = "N23G";
		SummaryReportUtils.tallyBadReadsMD(badRead, badMDReadCount);
		Assert.assertEquals((6), badMDReadCount.get("1M").get());
		
		badRead = "NATGNANNNC";
		SummaryReportUtils.tallyBadReadsMD(badRead, badMDReadCount);
		Assert.assertEquals((1), badMDReadCount.get("10M").get());
		
		badRead = "NT";
		SummaryReportUtils.tallyBadReadsMD(badRead, badMDReadCount);
		Assert.assertEquals((1), badMDReadCount.get("2M").get());
		
		badRead = "N";
		SummaryReportUtils.tallyBadReadsMD(badRead, badMDReadCount);
		Assert.assertEquals(7, badMDReadCount.get("1M").get());
		
		
		badRead = "ACGT1NNNN1TCGA1";
		SummaryReportUtils.tallyBadReadsMD(badRead, badMDReadCount);
		Assert.assertEquals((3), badMDReadCount.get("4M").get());
		
		
		//re-set collection
		badMDReadCount = new ConcurrentHashMap<String, AtomicLong>();
		badRead = "10AC^G5";
		SummaryReportUtils.tallyBadReadsMD(badRead, badMDReadCount);
		Assert.assertEquals((1), badMDReadCount.get("2M").get());
		Assert.assertEquals((1), badMDReadCount.get("1D").get());
		
		//re-set collection
		badMDReadCount = new ConcurrentHashMap<String, AtomicLong>();
		badRead = "^ACGNTACGNT10AC^G5ACGNT";
		SummaryReportUtils.tallyBadReadsMD(badRead, badMDReadCount);
		Assert.assertEquals((1), badMDReadCount.get("2M").get());
		Assert.assertEquals((1), badMDReadCount.get("5M").get());
		Assert.assertEquals((1), badMDReadCount.get("1D").get());
		Assert.assertEquals((1), badMDReadCount.get("10D").get());
		
		//re-set collection
		badMDReadCount = new ConcurrentHashMap<String, AtomicLong>();
		badRead = "^^^^";
		SummaryReportUtils.tallyBadReadsMD(badRead, badMDReadCount);
		Assert.assertTrue(badMDReadCount.isEmpty());
		
		
		// null string
		SummaryReportUtils.tallyBadReadsMD(null, badMDReadCount);
		
		// null map
		SummaryReportUtils.tallyBadReadsMD(null, null);
		
		// null map
		try {
			SummaryReportUtils.tallyBadReadsMD("AAA", null);
			Assert.fail("Should have thrown an AssertionError");
		} catch (AssertionError ae) {
			Assert.assertTrue(ae.getMessage().startsWith("Null map "));
		}
	}
	
	@Test
	public void testTallyQualScoresInvalid() {
		ConcurrentMap<Integer, AtomicLong> badQualCount = new ConcurrentHashMap<Integer, AtomicLong>();
		
		// null string and null seperator
		SummaryReportUtils.tallyQualScores(null, badQualCount, null);
		Assert.assertTrue(badQualCount.isEmpty());
		
		// empty string and null seperator
		String badQual = "";
		SummaryReportUtils.tallyQualScores(badQual, badQualCount, null);
		Assert.assertTrue(badQualCount.isEmpty());
		
		// empty string
		SummaryReportUtils.tallyQualScores(badQual, badQualCount, "");
		Assert.assertFalse(badQualCount.isEmpty());
		Assert.assertEquals(1, badQualCount.get(Integer.valueOf(0)).get());
		
		// valid string, but incorrect separator
		badQual = "1,1,1,1,1";
		try {
			SummaryReportUtils.tallyQualScores(badQual, badQualCount, "");
			Assert.fail("Should have thrown an exception");
		} catch (Exception e) {
			Assert.assertTrue(e.getMessage().startsWith("For input string"));
		}
	}
	
	@Test
	public void testAddPositionAndLengthToMap() {
		ConcurrentMap<Integer, AtomicLong> map = new ConcurrentHashMap<Integer, AtomicLong>();
		SummaryReportUtils.addPositionAndLengthToMap(map, 10, 100);
		
		Assert.assertEquals(100, map.size());
		Assert.assertNull(map.get(0));
		Assert.assertNull(map.get(9));
		Assert.assertNull(map.get(110));
		Assert.assertEquals(1, map.get(10).get());
		Assert.assertEquals(1, map.get(109).get());
		
		
		SummaryReportUtils.addPositionAndLengthToMap(map, 100, 50);
		Assert.assertEquals(140, map.size());
		Assert.assertNull(map.get(0));
		Assert.assertNull(map.get(9));
		Assert.assertNull(map.get(150));
		Assert.assertEquals(1, map.get(10).get());
		Assert.assertEquals(2, map.get(109).get());
		

		// adding 0 positions and size - should not affect anything...
		SummaryReportUtils.addPositionAndLengthToMap(map, 0, 0);
		Assert.assertEquals(140, map.size());
		Assert.assertNull(map.get(0));
		Assert.assertNull(map.get(9));
		Assert.assertNull(map.get(150));
		Assert.assertEquals(1, map.get(10).get());
		Assert.assertEquals(2, map.get(109).get());
		
		SummaryReportUtils.addPositionAndLengthToMap(map, 100, 10);
		Assert.assertEquals(140, map.size());
		Assert.assertNull(map.get(0));
		Assert.assertNull(map.get(9));
		Assert.assertNull(map.get(150));
		Assert.assertEquals(1, map.get(10).get());
		Assert.assertEquals(3, map.get(109).get());
		
		
		SummaryReportUtils.addPositionAndLengthToMap(map, 10000, 2);
		Assert.assertEquals(142, map.size());
		Assert.assertNull(map.get(0));
		Assert.assertNull(map.get(9));
		Assert.assertNull(map.get(150));
		Assert.assertNull(map.get(10002));
		Assert.assertEquals(1, map.get(10).get());
		Assert.assertEquals(3, map.get(109).get());
		Assert.assertEquals(1, map.get(10000).get());
		Assert.assertEquals(1, map.get(10001).get());
		
	}
	
	@Test
	public void testTallyQualScoresValid() {
		ConcurrentMap<Integer, AtomicLong> badQualCount = new ConcurrentHashMap<Integer, AtomicLong>();
		
		// valid string, valid seperator
		String qual = "1,1,1,1,1";
		SummaryReportUtils.tallyQualScores(qual, badQualCount, ",");
		Assert.assertEquals((1), badQualCount.get(Integer.valueOf(5)).get());
		
		qual = "1,2,3,4,5";
		SummaryReportUtils.tallyQualScores(qual, badQualCount, ",");
		Assert.assertEquals((2), badQualCount.get(Integer.valueOf(5)).get());
		
		qual = "9,9,9,9,9";
		SummaryReportUtils.tallyQualScores(qual, badQualCount, ",");
		Assert.assertEquals((3), badQualCount.get(Integer.valueOf(5)).get());
		
		qual = "1,2,3,9,9,10,11,12,13,14,15";
		SummaryReportUtils.tallyQualScores(qual, badQualCount, ",");
		Assert.assertEquals((4), badQualCount.get(Integer.valueOf(5)).get());
		
		// all values over 10
		qual = "10,11,12,13,14,15";
		SummaryReportUtils.tallyQualScores(qual, badQualCount, ",");
		Assert.assertEquals((4), badQualCount.get(Integer.valueOf(5)).get());
		Assert.assertEquals((1), badQualCount.get(Integer.valueOf(0)).get());
		
	}
	
	@Test
	public void testLengthMapToXML() throws Exception {
		Element root = createElement("testLengthMapToXML");
		
		ConcurrentNavigableMap<Integer, AtomicLong> map = new ConcurrentSkipListMap<Integer, AtomicLong>();
		SummaryReportUtils.lengthMapToXml(root, "test", map);
		
		Assert.assertTrue(root.hasChildNodes());
		Assert.assertEquals(1, root.getChildNodes().getLength());
		Assert.assertEquals("test", root.getChildNodes().item(0).getNodeName());
		Assert.assertFalse(root.getChildNodes().item(0).hasChildNodes());
		Assert.assertFalse(root.getChildNodes().item(0).hasAttributes());
		
		
		// same again this time with some data!
		map.put(100, new AtomicLong(42));
		map.put(101, new AtomicLong(41));
		map.put(102, new AtomicLong(40));
		map.put(103, new AtomicLong(39));
		map.put(104, new AtomicLong(38));
		map.put(105, new AtomicLong(37));
		SummaryReportUtils.lengthMapToXml(root, "test42", map);
		
		Assert.assertTrue(root.hasChildNodes());
		Assert.assertEquals(2, root.getChildNodes().getLength());
		Assert.assertEquals("test42", root.getChildNodes().item(1).getNodeName());
		
		Element element42ValueTally = (Element) root.getChildNodes().item(1).getChildNodes().item(0);
//		System.out.println("element42ValueTally = " + element42ValueTally.getNodeName());
		Assert.assertTrue(element42ValueTally.hasChildNodes());
		Assert.assertEquals(6, element42ValueTally.getChildNodes().getLength());
		
		// first element
		Element element42TallyItem = (Element) element42ValueTally.getChildNodes().item(0);
		Assert.assertFalse(element42TallyItem.hasChildNodes());
		Assert.assertTrue(element42TallyItem.hasAttributes());
		Assert.assertEquals(100, Integer.parseInt(element42TallyItem.getAttribute("value")));
		Assert.assertEquals(42, Integer.parseInt(element42TallyItem.getAttribute("count")));
		
		// second element
		element42TallyItem = (Element) element42ValueTally.getChildNodes().item(1);
		Assert.assertFalse(element42TallyItem.hasChildNodes());
		Assert.assertTrue(element42TallyItem.hasAttributes());
		Assert.assertEquals(101, Integer.parseInt(element42TallyItem.getAttribute("value")));
		Assert.assertEquals(41, Integer.parseInt(element42TallyItem.getAttribute("count")));
		
		// last element
		element42TallyItem = (Element) element42ValueTally.getChildNodes().item(5);
		Assert.assertFalse(element42TallyItem.hasChildNodes());
		Assert.assertTrue(element42TallyItem.hasAttributes());
		Assert.assertEquals(105, Integer.parseInt(element42TallyItem.getAttribute("value")));
		Assert.assertEquals(37, Integer.parseInt(element42TallyItem.getAttribute("count")));
	}
	
	@Test
	public void testBinnedLengthMapToRangeTallyXML() throws Exception {
		Element root = createElement("testBinnedLengthMapToRangeTallyXML");
		
		ConcurrentNavigableMap<Integer, AtomicLong> map = new ConcurrentSkipListMap<Integer, AtomicLong>();
		SummaryReportUtils.binnedLengthMapToRangeTallyXml(root, map);
		
		Assert.assertTrue(root.hasChildNodes());
		Assert.assertEquals(1, root.getChildNodes().getLength());
		Assert.assertEquals("RangeTally", root.getChildNodes().item(0).getNodeName());
		Assert.assertFalse(root.getChildNodes().item(0).hasChildNodes());
		Assert.assertFalse(root.getChildNodes().item(0).hasAttributes());
		
		
		// same again this time with some data!
		map.put(100, new AtomicLong(42));
		map.put(110, new AtomicLong(41));
		map.put(120, new AtomicLong(40));
		map.put(130, new AtomicLong(39));
		map.put(140, new AtomicLong(38));
		map.put(150, new AtomicLong(37));
//		map.put(getIntWrapper(100), new AtomicLong(42));
//		map.put(getIntWrapper(110), new AtomicLong(41));
//		map.put(getIntWrapper(120), new AtomicLong(40));
//		map.put(getIntWrapper(130), new AtomicLong(39));
//		map.put(getIntWrapper(140), new AtomicLong(38));
//		map.put(getIntWrapper(150), new AtomicLong(37));
		SummaryReportUtils.binnedLengthMapToRangeTallyXml(root, map);
		
		Assert.assertTrue(root.hasChildNodes());
		Assert.assertEquals(2, root.getChildNodes().getLength());
		Assert.assertEquals("RangeTally", root.getChildNodes().item(1).getNodeName());
		
		Element element42RangeTally = (Element) root.getChildNodes().item(1);
//		System.out.println("element42RangeTally = " + element42RangeTally.getNodeName());
		Assert.assertTrue(element42RangeTally.hasChildNodes());
		Assert.assertEquals(6, element42RangeTally.getChildNodes().getLength());
		
		// first element
		Element element42RangeTallyItem = (Element) element42RangeTally.getChildNodes().item(0);
		Assert.assertFalse(element42RangeTallyItem.hasChildNodes());
		Assert.assertTrue(element42RangeTallyItem.hasAttributes());
		Assert.assertEquals(100, Integer.parseInt(element42RangeTallyItem.getAttribute("start")));
		Assert.assertEquals(109, Integer.parseInt(element42RangeTallyItem.getAttribute("end")));
		Assert.assertEquals(42, Integer.parseInt(element42RangeTallyItem.getAttribute("count")));
		
		// second element
		element42RangeTallyItem = (Element) element42RangeTally.getChildNodes().item(1);
		Assert.assertFalse(element42RangeTallyItem.hasChildNodes());
		Assert.assertTrue(element42RangeTallyItem.hasAttributes());
		Assert.assertEquals(110, Integer.parseInt(element42RangeTallyItem.getAttribute("start")));
		Assert.assertEquals(119, Integer.parseInt(element42RangeTallyItem.getAttribute("end")));
		Assert.assertEquals(41, Integer.parseInt(element42RangeTallyItem.getAttribute("count")));
		
		// third element
		element42RangeTallyItem = (Element) element42RangeTally.getChildNodes().item(2);
		Assert.assertFalse(element42RangeTallyItem.hasChildNodes());
		Assert.assertTrue(element42RangeTallyItem.hasAttributes());
		Assert.assertEquals(120, Integer.parseInt(element42RangeTallyItem.getAttribute("start")));
		Assert.assertEquals(129, Integer.parseInt(element42RangeTallyItem.getAttribute("end")));
		Assert.assertEquals(40, Integer.parseInt(element42RangeTallyItem.getAttribute("count")));
		
		// last element
		element42RangeTallyItem = (Element) element42RangeTally.getChildNodes().item(5);
		Assert.assertFalse(element42RangeTallyItem.hasChildNodes());
		Assert.assertTrue(element42RangeTallyItem.hasAttributes());
		Assert.assertEquals(150, Integer.parseInt(element42RangeTallyItem.getAttribute("start")));
		Assert.assertEquals(159, Integer.parseInt(element42RangeTallyItem.getAttribute("end")));
		Assert.assertEquals(37, Integer.parseInt(element42RangeTallyItem.getAttribute("count")));
	}
	
	
	@Test
	public void testPostionSummaryMapToXml() throws Exception {
		Element root = createElement("testPostionSummaryMapToXml");
		
		ConcurrentMap<String, PositionSummary> map = new ConcurrentHashMap<String, PositionSummary>();
		SummaryReportUtils.postionSummaryMapToXml(root, "test", map);
		
		Assert.assertTrue(root.hasChildNodes());
		Assert.assertEquals(1, root.getChildNodes().getLength());
		Assert.assertEquals("test", root.getChildNodes().item(0).getNodeName());
		Assert.assertFalse(root.getChildNodes().item(0).hasChildNodes());
		Assert.assertFalse(root.getChildNodes().item(0).hasAttributes());
		
		PositionSummary ps = new PositionSummary(42);
		for (int i = 0 ; i <= 10000000 ; i++) {
			ps.addPosition(i);
		}
		
		
		// same again this time with some data!
		map.put("chr1", ps);
		map.put("chr2", new PositionSummary(41));
		map.put("chr3", new PositionSummary(40));
		map.put("chr4", new PositionSummary(39));
		map.put("chr5", new PositionSummary(38));
		map.put("chr6", new PositionSummary(37));
		SummaryReportUtils.postionSummaryMapToXml(root, "test42", map);
		
		Assert.assertTrue(root.hasChildNodes());
		Assert.assertEquals(2, root.getChildNodes().getLength());
		Assert.assertEquals("test42", root.getChildNodes().item(1).getNodeName());
		
		Element element42RName = (Element) root.getChildNodes().item(1).getChildNodes().item(0);
//		System.out.println("element42RName = " + element42RName.getNodeName());
		Assert.assertTrue(element42RName.hasChildNodes());
		Assert.assertTrue(element42RName.hasAttributes());
		Assert.assertEquals("chr1", element42RName.getAttribute("value"));
		Assert.assertEquals(0, Integer.parseInt(element42RName.getAttribute("minPosition")));
		Assert.assertEquals(10000000, Integer.parseInt(element42RName.getAttribute("maxPosition")));
		Assert.assertEquals(10000002, Integer.parseInt(element42RName.getAttribute("count")));
		Assert.assertEquals(1, element42RName.getChildNodes().getLength());

		// first element
		Element element42RangeTallyItem = (Element) element42RName.getChildNodes().item(0).getChildNodes().item(0);
		Assert.assertEquals(0, Integer.parseInt(element42RangeTallyItem.getAttribute("start")));
		Assert.assertEquals(999999, Integer.parseInt(element42RangeTallyItem.getAttribute("end")));
		Assert.assertEquals(1000001, Integer.parseInt(element42RangeTallyItem.getAttribute("count")));
		// second element
		element42RangeTallyItem = (Element) element42RName.getChildNodes().item(0).getChildNodes().item(1);
		Assert.assertEquals(1000000, Integer.parseInt(element42RangeTallyItem.getAttribute("start")));
		Assert.assertEquals(1999999, Integer.parseInt(element42RangeTallyItem.getAttribute("end")));
		Assert.assertEquals(1000000, Integer.parseInt(element42RangeTallyItem.getAttribute("count")));
		// last element
		element42RangeTallyItem = (Element) element42RName.getChildNodes().item(0).getChildNodes().item(9);
		Assert.assertEquals(9000000, Integer.parseInt(element42RangeTallyItem.getAttribute("start")));
		Assert.assertEquals(9999999, Integer.parseInt(element42RangeTallyItem.getAttribute("end")));
		Assert.assertEquals(1000000, Integer.parseInt(element42RangeTallyItem.getAttribute("count")));
		
		
		// next rname
		element42RName = (Element) root.getChildNodes().item(1).getChildNodes().item(1);
//		System.out.println("element42RName = " + element42RName.getNodeName());
		Assert.assertTrue(element42RName.hasChildNodes());
		Assert.assertTrue(element42RName.hasAttributes());
		Assert.assertEquals("chr2", element42RName.getAttribute("value"));
		Assert.assertEquals(41, Integer.parseInt(element42RName.getAttribute("minPosition")));
		Assert.assertEquals(41, Integer.parseInt(element42RName.getAttribute("maxPosition")));
		Assert.assertEquals(1, Integer.parseInt(element42RName.getAttribute("count")));
		Assert.assertEquals(1, element42RName.getChildNodes().getLength());
	}

	private Element createElement(String methodName) throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		DOMImplementation domImpl = builder.getDOMImplementation();
		Document doc = domImpl.createDocument(null, "SummaryReportUtilsTest." + methodName, null);
		return doc.getDocumentElement();
	}
}
