package org.qcmg.common.vcf.header;

import org.qcmg.common.util.Constants;
/**
 * Represents a info elements in a VCF file
 *
 * References: http://www.1000genomes.org/wiki/Analysis/Variant%20Call%20Format/vcf-variant-call-format-version-42
 *
 * FILTER fields should be described as follows (all keys are required):
 * 		##FILTER=<ID=ID,Description="description">
 *
 * @author Christina Xu
 */
public final class  VcfHeaderFilter extends VcfHeaderRecord{
 

	public VcfHeaderFilter(String line) {
		super(line);
		// Is this an Info line?
		if(this.type.equals(MetaType.FILTER)) 
			 parseLine(line);
		else throw new IllegalArgumentException("Can't create VcfHeaderFilter - line provided is not an FILTER definition: '" + line + "'");
		
		record = this;
	}
	
	public VcfHeaderFilter(String id, String description) throws Exception{ 
		 super(null );
		 this.id = id;
		 this.description = description;
		 type = MetaType.FILTER;
		 line = type.toString() +  "<ID=" + id//
	 				+ ",Description=\"" + description + "\"" + ">" ;
		 
		 record = this;
	}
	
	@Override
	public String toString() {
		if (line != Constants.NULL_STRING) 
			return (line.endsWith(Constants.NL_STRING))? line : line + Constants.NL  ;
  
		return  type.toString() +  "=<ID=" + id//
 				+ ",Description=\"" + description + "\"" //
				+ ">" +Constants.NL;
	 
	}
}
