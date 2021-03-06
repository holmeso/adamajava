/**
 * © Copyright The University of Queensland 2010-2014.
 * © Copyright QIMR Berghofer Medical Research Institute 2014-2016.
 *
 * This code is released under the terms outlined in the included LICENSE file.
 */
package org.qcmg.picard.fastq;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.qcmg.picard.SAMFileReaderFactory;

import picard.PicardException;
import picard.cmdline.CommandLineProgram;
import picard.cmdline.StandardOptionDefinitions;
import picard.cmdline.Option;
//import picard.cmdline.Usage;
import htsjdk.samtools.util.IOUtil;
 

import htsjdk.samtools.fastq.FastqRecord;
import htsjdk.samtools.fastq.FastqWriter;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMUtils;
import htsjdk.samtools.util.SequenceUtil;
import htsjdk.samtools.util.StringUtil;


/**
 * $Id: SamToFastq.java 1235 2012-07-21 12:49:32Z tfenne $
 * <p/>
 * Extracts read sequences and qualities from the input SAM/BAM file and writes them into
 * the output file in Sanger fastq format.
 * See <a href="http://maq.sourceforge.net/fastq.shtml">MAQ FastQ specification</a> for details.
 * In the RC mode (default is True), if the read is aligned and the alignment is to the reverse strand on the genome,
 * the read's sequence from input sam file will be reverse-complemented prior to writing it to fastq in order restore correctly
 * the original read sequence as it was generated by the sequencer.
 */
public class QSamToFastq extends CommandLineProgram {
	
 //   @Usage
    public String USAGE = "Extracts read sequences and qualities from the input SAM/BAM file and writes them into "+
        "the output file in Sanger fastq format. In the RC mode (default is True), if the read is aligned and the alignment is to the reverse strand on the genome, "+
        "the read's sequence from input SAM file will be reverse-complemented prior to writing it to fastq in order restore correctly "+
        "the original read sequence as it was generated by the sequencer.";

    @Option(doc="Input SAM/BAM file to extract reads from", shortName=StandardOptionDefinitions.INPUT_SHORT_NAME)
    public File INPUT ;

    @Option(shortName="F", doc="Output fastq file (single-end fastq or, if paired, first end of the pair fastq).", mutex={"OUTPUT_PER_RG"})
    public File FASTQ ;

    @Option(shortName="F2", doc="Output fastq file (if paired, second end of the pair fastq).", optional=true, mutex={"OUTPUT_PER_RG"})
    public File SECOND_END_FASTQ ;

    @Option(shortName="OPRG", doc="Output a fastq file per read group (two fastq files per read group if the group is paired).", optional=true, mutex={"FASTQ", "SECOND_END_FASTQ"})
    public boolean OUTPUT_PER_RG ;

    @Option(shortName="ODIR", doc="Directory in which to output the fastq file(s).  Used only when OUTPUT_PER_RG is true.", optional=true)
    public File OUTPUT_DIR;

    @Option(shortName="RC", doc="Re-reverse bases and qualities of reads with negative strand flag set before writing them to fastq", optional=true)
    public boolean RE_REVERSE = true;

    @Option(shortName="NON_PF", doc="Include non-PF reads from the SAM file into the output FASTQ files.")
    public boolean INCLUDE_NON_PF_READS = false;

    @Option(shortName="CLIP_ATTR", doc="The attribute that stores the position at which " +
            "the SAM record should be clipped", optional=true)
    public String CLIPPING_ATTRIBUTE;

    @Option(shortName="CLIP_ACT", doc="The action that should be taken with clipped reads: " +
            "'X' means the reads and qualities should be trimmed at the clipped position; " +
            "'N' means the bases should be changed to Ns in the clipped region; and any " +
            "integer means that the base qualities should be set to that value in the " +
            "clipped region.", optional=true)
    public String CLIPPING_ACTION;

    @Option(shortName="R1_TRIM", doc="The number of bases to trim from the beginning of read 1.")
    public int READ1_TRIM = 0;

    @Option(shortName="R1_MAX_BASES", doc="The maximum number of bases to write from read 1 after trimming. " +
            "If there are fewer than this many bases left after trimming, all will be written.  If this " +
            "value is null then all bases left after trimming will be written.", optional=true)
    public Integer READ1_MAX_BASES_TO_WRITE;

    @Option(shortName="R2_TRIM", doc="The number of bases to trim from the beginning of read 2.")
    public int READ2_TRIM = 0;

    @Option(shortName="R2_MAX_BASES", doc="The maximum number of bases to write from read 2 after trimming. " +
            "If there are fewer than this many bases left after trimming, all will be written.  If this " +
            "value is null then all bases left after trimming will be written.", optional=true)
    public Integer READ2_MAX_BASES_TO_WRITE;

    @Option(doc="If true, include non-primary alignments in the output.  Support of non-primary alignments in SamToFastq " +
    "is not comprehensive, so there may be exceptions if this is set to true and there are paired reads with non-primary alignments.")
    public boolean INCLUDE_NON_PRIMARY_ALIGNMENTS=false;

    private final Log log = Log.getInstance(QSamToFastq.class);
    
    public static void main(final String[] argv) {
        System.exit(new QSamToFastq().instanceMain(argv));
    }

    @Override
	protected int doWork() {
        IOUtil.assertFileIsReadable(INPUT);
        final SamReader reader =  SAMFileReaderFactory.createSAMFileReader( INPUT);
        final Map<String,SAMRecord> firstSeenMates = new HashMap<String,SAMRecord>();
        final Map<SAMReadGroupRecord, List<FastqWriter>> writers = getWriters(reader.getFileHeader().getReadGroups());

        for (final SAMRecord currentRecord : reader) {
            if (currentRecord.getNotPrimaryAlignmentFlag() && !INCLUDE_NON_PRIMARY_ALIGNMENTS)
                continue;

            // Skip non-PF reads as necessary
            if (currentRecord.getReadFailsVendorQualityCheckFlag() && !INCLUDE_NON_PF_READS)
                continue;

            final List<FastqWriter> fq = writers.get(currentRecord.getReadGroup());

            if (currentRecord.getReadPairedFlag()) {
                final String currentReadName = currentRecord.getReadName();
                final SAMRecord firstRecord = firstSeenMates.remove(currentReadName);
                if (firstRecord == null) {
                    firstSeenMates.put(currentReadName, currentRecord);
                } else {
                    assertPairedMates(firstRecord, currentRecord);

                    if (fq.size() == 1) {
                        if (OUTPUT_PER_RG) {
                            try {
								fq.add(new QFastqWriter(makeReadGroupFile(currentRecord.getReadGroup(), "_2")));
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
//                            fq.add(factory.newWriter(makeReadGroupFile(currentRecord.getReadGroup(), "_2")));
                        } else {
                            throw new PicardException("Input contains paired reads but no SECOND_END_FASTQ specified.");
                        }
                    }

                    final SAMRecord read1 =
                        currentRecord.getFirstOfPairFlag() ? currentRecord : firstRecord;
                    final SAMRecord read2 =
                        currentRecord.getFirstOfPairFlag() ? firstRecord : currentRecord;
                    writeRecord(read1, 1, fq.get(0), READ1_TRIM, READ1_MAX_BASES_TO_WRITE);
                    writeRecord(read2, 2, fq.get(1), READ2_TRIM, READ2_MAX_BASES_TO_WRITE);
 
                }
            } else {
                writeRecord(currentRecord, null, fq.get(0), READ1_TRIM, READ1_MAX_BASES_TO_WRITE);
            }

        }
        
      //XU: add rescue code to create empty sequence for missing mate reads
        for(String readName :  firstSeenMates.keySet())     
        	rescueLonelyRecord(  firstSeenMates.get(readName),  writers);
        firstSeenMates.clear(); 
        
        // Close all the fastq writers being careful to close each one only once!
        final IdentityHashMap<FastqWriter,FastqWriter> seen = new IdentityHashMap<FastqWriter, FastqWriter>();
        for (final List<FastqWriter> listOfWriters : writers.values()) {
            for (final FastqWriter w : listOfWriters) {
                if (!seen.containsKey(w)) {
                    w.close();
                    seen.put(w,w);
                }
            }
        }

        if (firstSeenMates.size() > 0) {
            throw new PicardException("Found " + firstSeenMates.size() + " unpaired mates");
        }

        return 0;
    }
    
  /**
   * Create an mate read with empty sequence and base, so any paired read can stay with its fake partner
   * @param read : read to be rescued
   * @param writers: a hash map of all writers
   */
    
    protected void rescueLonelyRecord(final SAMRecord read, Map<SAMReadGroupRecord, List<FastqWriter>> writers) {

    	SAMRecord emptyRead = new SAMRecord(read.getHeader());
    	emptyRead.setReadName(read.getReadName());
    	emptyRead.setReadBases( "".getBytes() );
    	emptyRead.setReadString("");
    	
        final List<FastqWriter> fq = writers.get(read.getReadGroup());
   	
        final SAMRecord read1 = read.getFirstOfPairFlag() ? read : emptyRead;
        final SAMRecord read2 = read.getFirstOfPairFlag() ? emptyRead : read;
        
        writeRecord(read1, 1, fq.get(0), READ1_TRIM, READ1_MAX_BASES_TO_WRITE);
        writeRecord(read2, 2, fq.get(1), READ2_TRIM, READ2_MAX_BASES_TO_WRITE);   	
    }
    
    

    /**
     * Gets the pair of writers for a given read group or, if we are not sorting by read group,
     * just returns the single pair of writers.
     */
    protected Map<SAMReadGroupRecord, List<FastqWriter>> getWriters(final List<SAMReadGroupRecord> samReadGroupRecords) {

        final Map<SAMReadGroupRecord, List<FastqWriter>> writerMap = new HashMap<SAMReadGroupRecord, List<FastqWriter>>();

        if (!OUTPUT_PER_RG) {
            // If we're not outputting by read group, there's only
            // one writer for each end.
            final List<FastqWriter> fqw = new ArrayList<FastqWriter>();

            IOUtil.assertFileIsWritable(FASTQ);
            IOUtil.openFileForWriting(FASTQ);
            try {
				fqw.add(new QFastqWriter(FASTQ));
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
//            fqw.add(factory.newWriter(FASTQ));

            if (SECOND_END_FASTQ != null) {
                IOUtil.assertFileIsWritable(SECOND_END_FASTQ);
                IOUtil.openFileForWriting(SECOND_END_FASTQ);
                try {
					fqw.add(new QFastqWriter(SECOND_END_FASTQ));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
//                fqw.add(factory.newWriter(SECOND_END_FASTQ));
            }
            // Store in map with null key, in case there are reads without read group.
            writerMap.put(null, fqw);
            // Also store for every read group in header.
            for (final SAMReadGroupRecord rg : samReadGroupRecords) {
                writerMap.put(rg, fqw);
            }
        } else {
            for (final SAMReadGroupRecord rg : samReadGroupRecords) {
                final List<FastqWriter> fqw = new ArrayList<FastqWriter>();

                try {
					fqw.add(new QFastqWriter(makeReadGroupFile(rg, "_1")));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
//                fqw.add(factory.newWriter(makeReadGroupFile(rg, "_1")));
                writerMap.put(rg, fqw);
            }
        }
        return writerMap;
    }
    

    protected File makeReadGroupFile(final SAMReadGroupRecord readGroup, final String preExtSuffix) {
        String fileName = readGroup.getPlatformUnit();
        if (fileName == null) fileName = readGroup.getReadGroupId();
        fileName = IOUtil.makeFileNameSafe(fileName);
        if(preExtSuffix != null) fileName += preExtSuffix;
        fileName += ".fastq";

        final File result = (OUTPUT_DIR != null)
                          ? new File(OUTPUT_DIR, fileName)
                          : new File(fileName);
        IOUtil.assertFileIsWritable(result);
        return result;
    }

    protected void writeRecord(final SAMRecord read, final Integer mateNumber, final FastqWriter writer,
                     final int basesToTrim, final Integer maxBasesToWrite) {
        final String seqHeader = mateNumber==null ? read.getReadName() : read.getReadName() + "/"+ mateNumber;
        String readString = read.getReadString();
        String baseQualities = read.getBaseQualityString();

        // If we're clipping, do the right thing to the bases or qualities
        if (CLIPPING_ATTRIBUTE != null) {
            final Integer clipPoint = (Integer)read.getAttribute(CLIPPING_ATTRIBUTE);
            if (clipPoint != null) {
                if (CLIPPING_ACTION.equalsIgnoreCase("X")) {
                    readString = clip(readString, clipPoint, null,
                            !read.getReadNegativeStrandFlag());
                    baseQualities = clip(baseQualities, clipPoint, null,
                            !read.getReadNegativeStrandFlag());

                }
                else if (CLIPPING_ACTION.equalsIgnoreCase("N")) {
                    readString = clip(readString, clipPoint, 'N',
                            !read.getReadNegativeStrandFlag());
                }
                else {
                    final char newQual = SAMUtils.phredToFastq(
                            new byte[] { (byte)Integer.parseInt(CLIPPING_ACTION)}).charAt(0);
                    baseQualities = clip(baseQualities, clipPoint, newQual,
                            !read.getReadNegativeStrandFlag());
                }
            }
        }
        if ( RE_REVERSE && read.getReadNegativeStrandFlag() ) {
            readString = SequenceUtil.reverseComplement(readString);
            baseQualities = StringUtil.reverseString(baseQualities);
        }
        if (basesToTrim > 0) {
            readString = readString.substring(basesToTrim);
            baseQualities = baseQualities.substring(basesToTrim);
        }

        if (maxBasesToWrite != null && maxBasesToWrite < readString.length()) {
            readString = readString.substring(0, maxBasesToWrite);
            baseQualities = baseQualities.substring(0, maxBasesToWrite);
        }

        writer.write(new FastqRecord(seqHeader, readString, "", baseQualities));

    }

    /**
     * Utility method to handle the changes required to the base/quality strings by the clipping
     * parameters.
     *
     * @param src           The string to clip
     * @param point         The 1-based position of the first clipped base in the read
     * @param replacement   If non-null, the character to replace in the clipped positions
     *                      in the string (a quality score or 'N').  If null, just trim src
     * @param posStrand     Whether the read is on the positive strand
     * @return String       The clipped read or qualities
     */
    private String clip(final String src, final int point, final Character replacement, final boolean posStrand) {
        final int len = src.length();
        String result = posStrand ? src.substring(0, point-1) : src.substring(len-point+1);
        if (replacement != null) {
            if (posStrand) {
                for (int i = point; i <= len; i++ ) {
                    result += replacement;
                }
            }
            else {
                for (int i = 0; i <= len-point; i++) {
                    result = replacement + result;
                }
            }
        }
        return result;
    }

    protected void assertPairedMates(final SAMRecord record1, final SAMRecord record2) {
        if (! (record1.getFirstOfPairFlag() && record2.getSecondOfPairFlag() ||
               record2.getFirstOfPairFlag() && record1.getSecondOfPairFlag() ) ) {
            throw new PicardException("Illegal mate state: " + record1.getReadName());
        }
    }


    /**
    * Put any custom command-line validation in an override of this method.
    * clp is initialized at this point and can be used to print usage and access argv.
     * Any options set by command-line parser can be validated.
    * @return null if command line is valid.  If command line is invalid, returns an array of error
    * messages to be written to the appropriate place.
    */
    @Override
	protected String[] customCommandLineValidation() {
        if ((CLIPPING_ATTRIBUTE != null && CLIPPING_ACTION == null) ||
            (CLIPPING_ATTRIBUTE == null && CLIPPING_ACTION != null)) {
            return new String[] {
                    "Both or neither of CLIPPING_ATTRIBUTE and CLIPPING_ACTION should be set." };
        }
        if (CLIPPING_ACTION != null) {
            if (CLIPPING_ACTION.equals("N") || CLIPPING_ACTION.equals("X")) {
                // Do nothing, this is fine
            }
            else {
                try {
                    Integer.parseInt(CLIPPING_ACTION);
                }
                catch (NumberFormatException nfe) {
                    return new String[] {"CLIPPING ACTION must be one of: N, X, or an integer"};
                }
            }
        }
        if ((OUTPUT_PER_RG && OUTPUT_DIR == null) || ((!OUTPUT_PER_RG) && OUTPUT_DIR != null)) {
            return new String[] {
                    "If OUTPUT_PER_RG is true, then OUTPUT_DIR should be set. " +
                    "If " };
        }


        return null;
    }
}
