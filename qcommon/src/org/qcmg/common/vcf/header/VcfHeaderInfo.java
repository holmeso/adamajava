package org.qcmg.common.vcf.header;


public final class VcfHeaderInfo extends VcfHeaderRecord{

	public VcfHeaderInfo(String line) throws Exception {
		super(line);	
		 
		// Is this an Info line?
		if (line.startsWith(MetaType.INFO.toString())) {			
			 parseLine(line);
		} else throw new RuntimeException("Line provided is not an INFO definition: '" + line + "'");
		record = this;

	}
 
 /**
  * it create an INFO vcf header record, eg. ##INFO=<ID=id,NUMBER=number/infoNumber,Type=infoType,Description=description,Source=source,Version=version>
  * @param id 
  * @param infoNumbe
  * @param number: this number will show on vcf header record if infoNumber is MetaType.NUMBER
  * @param infoType
  * @param description
  * @param source: it will show on vcf header if source != null
  * @param version: it will show on vcf header if source != null
  * @throws Exception
  */
	public VcfHeaderInfo(String id, VcfInfoNumber infoNumber, int number, VcfInfoType infoType, String description, String source, String version) throws Exception {

		super(null);
		this.id = id;
		vcfInfoNumber = infoNumber;
		this.number = number;
		vcfInfoType = infoType;
		this.description = description;
		this.version = version;
		this.source = source;
		 
		this.type = MetaType.INFO; //type should bf line otherwise exception
		this.line = type.toString() + "<ID=" + id//
				+ ",Number=" + (number >= 0 ? number : vcfInfoNumber.toString()) //
//				+ ",Number=" +vcfInfoNumber.toString()
				+ ",Type=" + vcfInfoType.toString() //
				+ ",Description=\"" + description + "\"" //
				+ (source == null ? "" : ",Source=\"" + source + "\"" )//
				+ (version == null ? "" : ",Version=\"" + version + "\"" ) + ">" ;
		record = this;
	}
	
	@Override
	public String toString() {
		if (line != null) return line;

		return type.toString() + "<ID=" + id//
				+ ",Number=" + (number >= 0 ? number : vcfInfoNumber) //
				+ ",Type=" + vcfInfoType //
				+ ",Description=\"" + description + "\"" //
				+ (source == null ? "" : ",Source=\"" + source + "\"" )//
				+ (version == null ? "" : ",Version=\"" + version + "\"" )//
				+ ">" //
		;
	}
	

	
	
}
