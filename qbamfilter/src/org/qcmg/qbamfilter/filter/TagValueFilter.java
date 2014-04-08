/**
 * © Copyright The University of Queensland 2010-2014.  This code is released under the terms outlined in the included LICENSE file.
 */
package org.qcmg.qbamfilter.filter;

import net.sf.picard.filter.SamRecordFilter;
import net.sf.samtools.SAMRecord;
import net.sf.samtools.SAMTagUtil;

public final class TagValueFilter implements SamRecordFilter{

	private static SAMTagUtil stu = SAMTagUtil.getSingleton();
    private final String tag;
    private final short tagShort;
    private final String value;
    private final Comparator op;


    /**
     * initilize optinal field name, comparator and field value
     * @parm Tag : the optional field name,it will be convert ot uppercase automatically.
     * @param comp: see details of valid comparator on org.qcmg.qbamfilter.filter.Comparator.
     * @param value:  a string value.
     * @throws Exception
     * See usage on method filterout.
     */
    public TagValueFilter(String Tag, Comparator comp, String value )throws Exception{
        tag = Tag.toUpperCase();
        tagShort = stu.makeBinaryTag(Tag);
        this.value = value;
        op = comp;
    }

    /**
     * check the optional filed in SAMRecord. return true if that field value is satified by the condition
     * @param record: a SAMRecord
     * @return true if this potional field is satisfied with the query
     * Usage example: if you want filter out all reads with field "ZM",and its value is one.
     * CigarFilter myfilter = new TagValueFilter("ZM",Comparator.Equal, "1" );
     * if(myfilter.filterout(record) == true){ System.out.println(record.toString);}
     */
    @Override
    public boolean filterOut(final SAMRecord record){
        //if that tag fileld is not exists, it return null
        Object ob = record.getAttribute(tagShort);

        if(ob != null){
            return op.eval(ob.toString(),value );
        }

        return false;

    }

    /**
     * It is an inherited method and return false only. 
     */
	@Override @Deprecated
	public boolean filterOut(SAMRecord arg0, SAMRecord arg1) {
		// TODO Auto-generated method stub
		return false;
	}
   
}