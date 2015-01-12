package au.edu.qimr.qannotate.modes;

import java.io.File;
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
import org.qcmg.common.vcf.VcfRecord;
import org.qcmg.common.vcf.header.VcfHeader;
import org.qcmg.common.vcf.header.VcfHeaderRecord;
import org.qcmg.common.vcf.header.VcfHeaderRecord.MetaType;
import org.qcmg.common.vcf.header.VcfHeaderUtils;
import org.qcmg.vcf.VCFFileReader;
import org.qcmg.vcf.VCFFileWriter;

import au.edu.qimr.qannotate.Main;
import au.edu.qimr.qannotate.Messages;

public abstract class AbstractMode {
	private static final DateFormat df = new SimpleDateFormat("yyyyMMdd");
	protected final Map<ChrPosition,VcfRecord> positionRecordMap = new HashMap<ChrPosition,VcfRecord>();
	protected VcfHeader header;
	protected String inputUuid;
 	
	protected void inputRecord(File f) throws Exception{
		
        //read record into RAM, meanwhile wipe off the ID field value;
 
        try(VCFFileReader reader = new VCFFileReader(f)) {
        	header = reader.getHeader();
           	for(final VcfHeaderRecord hr : header)
        		if(hr.getMetaType().equals(MetaType.META) && hr.getId().equalsIgnoreCase(VcfHeaderUtils.STANDARD_UUID_LINE)){
        			inputUuid = hr.getDescription();
        			break;
        		}
        	
        	//no chr in front of position
			for (final VcfRecord qpr : reader) 
				positionRecordMap.put(new ChrPosition(qpr.getChromosome(), qpr.getPosition(), qpr.getPosition() + qpr.getRef().length() - 1 ), qpr);
			
		}  
        
	}

	abstract void addAnnotation(String dbfile) throws Exception;
	
	
	protected void writeVCF(File outputFile ) throws Exception {
		 
//		logger.info("Writing VCF output");	 		
		//get Q_EXEC or #Q_DCCMETA  org.qcmg.common.meta.KeyValue.java or org.qcmg.common.meta.QExec.java	
		final List<ChrPosition> orderedList = new ArrayList<ChrPosition>(positionRecordMap.keySet());
		Collections.sort(orderedList);
		
		try(VCFFileWriter writer = new VCFFileWriter( outputFile)) {			
			for(final VcfHeaderRecord record: header)  writer.addHeader(record.toString());
			for (final ChrPosition position : orderedList) {				
				final VcfRecord record = positionRecordMap.get(position); 
				writer.add( record );				 
			}
		}  
		
	}
	
	protected void reheader(String cmd, String inputVcfName) throws Exception{	

		final String version = Main.class.getPackage().getImplementationVersion();
		final String pg = Messages.getProgramName();
		final String fileDate = df.format(Calendar.getInstance().getTime());
		final String uuid = QExec.createUUid();

		//move input uuid into preuuid
		header.replace(new VcfHeaderRecord(VcfHeaderUtils.STANDARD_INPUT_LINE + "=" + inputUuid + ":"+ inputVcfName) );
		header.add( new VcfHeaderRecord( MetaType.OTHER.toString() + cmd));
		
		header.updateHeader( new VcfHeaderRecord(VcfHeaderUtils.CURRENT_FILE_VERSION),
				new VcfHeaderRecord(VcfHeaderUtils.STANDARD_FILE_DATE + "=" + fileDate ),
				new VcfHeaderRecord(VcfHeaderUtils.STANDARD_UUID_LINE + "=" + uuid ),
				new VcfHeaderRecord(VcfHeaderUtils.STANDARD_SOURCE_LINE + "=" + pg+"-"+version) );

	}	
}
