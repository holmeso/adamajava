package au.edu.qimr.qannotate.modes;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.qcmg.common.meta.QExec;
import org.qcmg.common.model.ChrPosition;
import org.qcmg.common.string.StringUtils;
import org.qcmg.common.util.Constants;
import org.qcmg.common.vcf.VcfRecord;
import org.qcmg.common.vcf.header.VcfHeader;
import org.qcmg.common.vcf.header.VcfHeaderUtils;
import org.qcmg.vcf.VCFFileReader;
import org.qcmg.vcf.VCFFileWriter;

import au.edu.qimr.qannotate.Main;

public abstract class AbstractMode {
	private static final DateFormat df = new SimpleDateFormat("yyyyMMdd");
	protected final Map<ChrPosition,VcfRecord> positionRecordMap = new HashMap<ChrPosition,VcfRecord>();
	
	protected VcfHeader header;
	protected String inputUuid;	
	protected int test_column = -2; //can't be -1 since will "+1"
	protected int control_column = -2;
	protected String controlSample = null; 
	protected String testSample = null; 
 	
	protected void inputRecord(File f) throws IOException{
		
        //read record into RAM, meanwhile wipe off the ID field value;
 
        try (VCFFileReader reader = new VCFFileReader(f)) {
        		header = reader.getHeader();
        		VcfHeader.Record uuidRecord = header.getUUID();
        		if (null != uuidRecord) {
        			inputUuid = StringUtils.getValueFromKey(uuidRecord.getData(), VcfHeaderUtils.STANDARD_UUID_LINE);
        		}
        	
        	//no chr in front of position
			for (final VcfRecord qpr : reader) {
				positionRecordMap.put(qpr.getChrPosition(), qpr);
			}
		} 
        
	}
 
	protected void retriveDefaultSampleColumn(){
		retriveSampleColumn(null, null, null);
		
	}
	
	/**
	 * it retrive the sample column number. eg. if the second column after "FORMAT" is for the sample named "testSample", then it will report "2" to related variable
	 * @param testSample:   testSample column name located after "FORMAT" column, put null here if vcf header already exisit qControlSample
	 * @param controlSample:  controlSample column name located after "FORMAT" column, put null here if vcf header already exisit qTestSample
	 * @param header: if null, it will point to this class's header; 
	 */
	protected void retriveSampleColumn(String test, String control, VcfHeader header){
		if (header == null) {
			header = this.header;
		}
		
		 controlSample = control;
		 testSample = test;
		
		 if (null == controlSample || null == testSample) {
			for (final VcfHeader.Record hr : header.getMetaRecords()) {
		    		if (null == controlSample &&  hr.getData().indexOf(VcfHeaderUtils.STANDARD_CONTROLSAMPLE) != -1) {
		    			controlSample =  StringUtils.getValueFromKey(hr.getData(), VcfHeaderUtils.STANDARD_CONTROLSAMPLE);
		    		} else if ( null == testSample &&  hr.getData().indexOf(VcfHeaderUtils.STANDARD_TESTSAMPLE) != -1) { 
		    			 testSample = StringUtils.getValueFromKey(hr.getData(), VcfHeaderUtils.STANDARD_TESTSAMPLE);
		    		}
			}
		 }
 
	   if(testSample == null || controlSample == null)
		   throw new RuntimeException(" Missing qControlSample or qTestSample  from VcfHeader; please specify on command line!");
	   
	   final String[] samples = header.getSampleId();	
	   	   
		//incase both point into same column
		for(int i = 0; i < samples.length; i++){ 
			if(samples[i].equalsIgnoreCase(testSample))
				test_column = i + 1;
			//else if(samples[i].equalsIgnoreCase(controlSample))
			if(samples[i].equalsIgnoreCase(controlSample))
				control_column = i + 1;
		}
		
		if(test_column <= 0 )
			throw new RuntimeException("can't find test sample id from vcf header line: " + testSample);
		if(control_column <= 0  )
			throw new RuntimeException("can't find normal sample id from vcf header line: " + controlSample);	  				 
	    				 		 
	}

	abstract void addAnnotation(String dbfile) throws Exception;
	
	
	protected void writeVCF(File outputFile ) throws IOException {
		 
//		logger.info("Writing VCF output");	 		
		//get Q_EXEC or #Q_DCCMETA  org.qcmg.common.meta.KeyValue.java or org.qcmg.common.meta.QExec.java	
		final List<ChrPosition> orderedList = new ArrayList<ChrPosition>(positionRecordMap.keySet());
		Collections.sort(orderedList);
		
		try(VCFFileWriter writer = new VCFFileWriter( outputFile)) {			
			for(final VcfHeader.Record record: header)  {
				writer.addHeader(record.toString());
			}
			for (final ChrPosition position : orderedList) {				
				VcfRecord record = positionRecordMap.get(position); 
				writer.add( record );				 
			}
		}  
		
		
	}
	
	protected void reheader(String cmd, String inputVcfName) {	
		String version = Main.class.getPackage().getImplementationVersion();
		String pg = Main.class.getPackage().getImplementationTitle();
		final String fileDate = df.format(Calendar.getInstance().getTime());
		final String uuid = QExec.createUUid();

		//move input uuid into preuuid
		header.replace(VcfHeaderUtils.STANDARD_INPUT_LINE + "=" + inputUuid + ":"+ inputVcfName);
		
		header.parseHeaderLine(VcfHeaderUtils.CURRENT_FILE_VERSION);
		header.parseHeaderLine(VcfHeaderUtils.STANDARD_FILE_DATE + "=" + fileDate);
		header.parseHeaderLine(VcfHeaderUtils.STANDARD_UUID_LINE + "=" + uuid);
		header.parseHeaderLine(VcfHeaderUtils.STANDARD_SOURCE_LINE + "=" + pg+"-"+version);
		
		
	    if(version == null) version = Constants.NULL_STRING;
	    if(pg == null ) pg = Constants.NULL_STRING;
	    if(cmd == null) cmd = Constants.NULL_STRING;
		
		 VcfHeaderUtils.addQPGLineToHeader(header, pg, version, cmd);
		
	}	
}
