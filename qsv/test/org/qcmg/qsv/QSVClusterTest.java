package org.qcmg.qsv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.sf.samtools.SAMFileHeader.SortOrder;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.qcmg.qsv.discordantpair.DiscordantPairCluster;
import org.qcmg.qsv.discordantpair.MatePair;
import org.qcmg.qsv.discordantpair.PairGroup;
import org.qcmg.qsv.softclip.SoftClipCluster;
import org.qcmg.qsv.splitread.SplitReadContig;
import org.qcmg.qsv.util.QSVConstants;
import org.qcmg.qsv.util.TestUtil;

public class QSVClusterTest {
	
	private QSVCluster record;
	private QSVParameters tumor;
	private QSVParameters normal;
	private List<QSVCluster> list;
	
	@Rule
	public TemporaryFolder testFolder = new TemporaryFolder();
	

    @Before
    public void setUp() throws IOException, Exception {
    	File tumorBam = TestUtil.createSamFile(testFolder.newFile("tumor.bam").getAbsolutePath(), PairGroup.AAC, SortOrder.queryname, true);
    	File normalBam = TestUtil.createSamFile(testFolder.newFile("normal.bam").getAbsolutePath(), PairGroup.AAC, SortOrder.queryname, true);
        tumor = TestUtil.getQSVParameters(testFolder, normalBam.getAbsolutePath(), tumorBam.getAbsolutePath(), true, "both");
        normal = TestUtil.getQSVParameters(testFolder, normalBam.getAbsolutePath(), tumorBam.getAbsolutePath(), false, "both"); 
    	list = new ArrayList<QSVCluster>();
    }
    
    @After
    public void tearDown() {
    	record = null;

    	list = null;
    	tumor = null;
    	normal = null;
    }
    
    @Test
    public void testFindGermline() throws IOException, Exception {
    	record = TestUtil.setupQSVCluster(PairGroup.AAC, "germline", testFolder, "chr7", "chr7", true, false);    	
    	record.findGermline();    	
    	assertTrue(record.isGermline());
    	
    	record = TestUtil.setupQSVCluster(PairGroup.AAC, "somatic", testFolder, "chr7", "chr7", false, false);    	
    	record.findGermline();    	
    	assertFalse(record.isGermline());
    }
    
    @Test 
    public void testFindClipOverlap() throws IOException, Exception {
    	record = TestUtil.setupQSVCluster(PairGroup.AAC, "germline", testFolder, "chr10", "chr10", true, false);    	
    	assertEquals(1, record.getClipRecords().size());
    	assertTrue(record.findClipOverlap(TestUtil.setUpClipRecord("chr10", "chr10", false, false)));
    	assertEquals(2, record.getClipRecords().size());
    }
    
    @Test
    public void testFindClusterOverlapIsFalse() throws IOException, Exception {
    	DiscordantPairCluster cluster = TestUtil.setupSolidCluster(PairGroup.AAC, "somatic", testFolder, "chr7", "chr7");
    	record = new QSVCluster(cluster, false,  "id");
    	boolean test = record.findClusterOverlap(TestUtil.setUpClipRecord("chr10", "chr10", false, false));
    	assertFalse(test);
    }
    
    @Test
    public void testFindClusterOverlapIsTrue() throws IOException, Exception {
    	DiscordantPairCluster cluster = TestUtil.setupHiseqCluster("somatic", testFolder, "1");
    	record = new QSVCluster(cluster, false,  "id");
    	boolean test = record.findClusterOverlap(TestUtil.setUpClipRecord("chr10", "chr10", false, false));
    	assertTrue(test);
    }
    
    @Test
    public void testGetOverlapWithCategory1() throws IOException, Exception {
    	DiscordantPairCluster cluster = TestUtil.setupHiseqCluster("somatic", testFolder, "1");
    	record = new QSVCluster(cluster, false,  "id"); 	
//    	89700049
//    	89700300
//    	89712340
//    	89712546
    	//cat1
    	assertFalse(record.getOverlap(true, 89700465));
    	assertTrue(record.getOverlap(true, 89700265));
    	assertFalse(record.getOverlap(false, 89700265));
    	assertTrue(record.getOverlap(false, 89712345));
    }
    
    @Test
    public void testGetOverlapWithCategory2() throws IOException, Exception {
    	DiscordantPairCluster cluster = TestUtil.setupHiseqCluster("somatic", testFolder, "2");
    	record = new QSVCluster(cluster, false,  "id");
    	assertFalse(record.getOverlap(true, 89700265));
    	assertTrue(record.getOverlap(true, 89700065));
    	assertFalse(record.getOverlap(false, 89700265));
    	assertTrue(record.getOverlap(false, 89712545));
    }
    
    @Test
    public void testGetOverlapWithCategory3() throws IOException, Exception {
    	DiscordantPairCluster cluster = TestUtil.setupHiseqCluster("somatic", testFolder, "3");
    	record = new QSVCluster(cluster, false,  "id");
    	assertFalse(record.getOverlap(true, 89700465));
    	assertTrue(record.getOverlap(true, 89700265));
    	assertFalse(record.getOverlap(false, 89700265));
    	assertTrue(record.getOverlap(false, 89712545));    
    }
    
    @Test
    public void testGetOverlapWithCategory4() throws IOException, Exception {
    	DiscordantPairCluster cluster = TestUtil.setupHiseqCluster("somatic", testFolder, "4");
    	record = new QSVCluster(cluster, false,  "id");
    	assertFalse(record.getOverlap(true, 89700265));
    	assertTrue(record.getOverlap(true, 89700065));
    	assertFalse(record.getOverlap(false, 89700265));
    	assertTrue(record.getOverlap(false, 89712345));
    }
    
    @Test
    public void testGetGermlineRatio() throws IOException, Exception {
    	DiscordantPairCluster cluster = TestUtil.setupHiseqCluster("somatic", testFolder, "1");
    	MatePair m =cluster.getClusterMatePairs().get(0);    	
    	record = new QSVCluster(cluster, false,  "id");
    	
    	assertFalse(record.getPotentialGermline());
    	record.getPairRecord().setLowConfidenceNormalMatePairs(1);
    	assertTrue(record.getPotentialGermline());
    }
    
    @Test
    public void testGetDataString() throws Exception {
    	DiscordantPairCluster cluster = TestUtil.setupHiseqCluster("somatic", testFolder, "1");
    	record = new QSVCluster(cluster, false,  "id");
    	record.addQSVClipRecord(TestUtil.setUpClipRecord("chr10", "chr10", false, false));
    	record.setIdParameters("sv1", "test", "testsample", new Date());
    	assertEquals(37, record.getDataString("dcc", "TD", "ND", true, "solid").split("\t").length);
    	//assertEquals(8,record.getDataString("vcf", "TD", "ND", true).split("\t").length);
    	assertEquals(20,record.getDataString("tab", "TD", "ND", true, "solid").split("\t").length);
    	assertEquals(1,record.getDataString("verbose", "TD", "ND", true, "solid").split("\t").length);
    	assertEquals(5,record.getDataString("qprimer", "TD", "ND", true, "solid").split("\t").length);
    	assertEquals(23,record.getDataString("softclip", "TD", "ND", true, "solid").split("\t").length);
    }
    
    @Test
    public void testConfidenceLevel() throws IOException, Exception {
    	//3
    	DiscordantPairCluster cluster = TestUtil.setupHiseqCluster("somatic", testFolder, "1"); 	
    	record = new QSVCluster(cluster, false,  "id");
    	assertEquals(QSVConstants.LEVEL_LOW, record.getConfidenceLevel());
    	
    	//5
    	record.getPairRecord().setLowConfidenceNormalMatePairs(1);
    	assertEquals(QSVConstants.LEVEL_GERMLINE, record.getConfidenceLevel());    	
    	
    	//1
    	cluster = TestUtil.setupHiseqCluster("somatic", testFolder, "1");
    	record = new QSVCluster(cluster, false,  "id");
    	SoftClipCluster clip = TestUtil.setUpClipRecord("chr10", "chr10", false, false);
    	record.findClusterOverlap(clip);
    	SplitReadContig contig = createMock(SplitReadContig.class);
    	expect(contig.getIsPotentialSplitRead()).andReturn(true);
    	replay(contig);
    	record.setSplitReadContig(contig);
    	assertEquals("1", record.getConfidenceLevel());  
    	
    	//2    	
    	cluster = TestUtil.setupHiseqCluster("somatic", testFolder, "1");
    	record = new QSVCluster(cluster, false,  "id");
    	record.findClusterOverlap(TestUtil.setUpClipRecord("chr10", "chr10", false, true));
    	assertEquals(QSVConstants.LEVEL_LOW, record.getConfidenceLevel());  
    	
    	//4
    	record = new QSVCluster(TestUtil.setUpClipRecord("chr10", "chr10", false, false), "test");
    	assertEquals(QSVConstants.LEVEL_LOW, record.getConfidenceLevel());
    	
    	//6
    	record = new QSVCluster(TestUtil.setUpClipRecord("chr10", "chr10", false, true), "test");
    	assertEquals(QSVConstants.LEVEL_SINGLE_CLIP, record.getConfidenceLevel());
    }
	
}