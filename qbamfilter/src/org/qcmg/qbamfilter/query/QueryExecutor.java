/**
 * © Copyright The University of Queensland 2010-2014.  This code is released under the terms outlined in the included LICENSE file.
 */

package org.qcmg.qbamfilter.query;

import net.sf.picard.filter.SamRecordFilter;
import net.sf.samtools.SAMRecord;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.CommonTreeNodeStream;
import org.qcmg.common.log.QLogger;
import org.qcmg.common.log.QLoggerFactory;
import org.qcmg.qbamfilter.grammars.queryLexer;
import org.qcmg.qbamfilter.grammars.queryParser;
import org.qcmg.qbamfilter.grammars.queryTree;

public class QueryExecutor {
    
    private SamRecordFilter queryExecutor;

    
    private final QLogger logger = QLoggerFactory.getLogger(QueryExecutor.class);
    /**
     * @param query
     * It Check the query and create an CommonTree. it run once for each query from command line.
     */
    public QueryExecutor(String query) throws Exception{
    		
        try{
            queryLexer myLexer = new queryLexer(new ANTLRStringStream( query ));
            queryParser myParser = new queryParser(new CommonTokenStream(myLexer));
            queryParser.script_return myScript = myParser.script();
            CommonTree ParserTree = (CommonTree) myScript.getTree();
            CommonTreeNodeStream myStream = new CommonTreeNodeStream(ParserTree);
            queryTree  myAST = new queryTree(myStream);      
            queryTree.script_return result = myAST.script();
            queryExecutor =  result.exp;
        }catch(RecognitionException e ){
        	logger.error("RecognitionException caught while instantiating QueryExecutor", e);
        	throw  e;
        }
        logger.info("Setting up QueryExecutor with query: " + query);
        
    }

    /**
     * @param record
     * @return
     * @throws Exception
     * Check each SAMRecord whether it is valid or not,
     * then check whether it satisfied with query.
     */
    public boolean Execute(SAMRecord record) throws Exception{         
                   
        try{
        	return queryExecutor.filterOut(record);

       // }catch(RecognitionException e ){ 
        //    throw new Exception(Thread.currentThread().getName() + " " + e.toString() + " in QyertExecutor");
        }catch (Exception e) {	
        	 if( record == null)
        		 throw new Exception(Thread.currentThread().getName() + "inside QueryExecutor (record is null)" );   
        	 else
			     throw new Exception(Thread.currentThread().getName() + " "+ e.toString() );
 
		}  
    }
    
//    private boolean checkValid(SAMRecord record){
//    	List<SAMValidationError> Errs = record.isValid();
//        if( Errs != null){
//            System.err.println("invalid read: " + record);
//            for(SAMValidationError err: Errs){
//                System.err.println(err.getMessage());
//            }
//            return false;
//        }   
//    	return true;
//    }

}