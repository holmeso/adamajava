/**
 * © Copyright The University of Queensland 2010-2014.  This code is released under the terms outlined in the included LICENSE file.
 */
package org.qcmg.qsv.softclip;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMRecord;
import net.sf.samtools.SAMRecordIterator;

import org.qcmg.picard.SAMFileReaderFactory;
import org.qcmg.qsv.QSVParameters;
import org.qcmg.qsv.assemble.QSVAssemble;
import org.qcmg.qsv.blat.BLAT;
import org.qcmg.qsv.splitread.UnmappedRead;
import org.qcmg.qsv.util.QSVConstants;
import org.qcmg.qsv.util.QSVUtil;

public class SoftClipCluster implements Comparable<SoftClipCluster> {
	
	String name;
	Breakpoint leftBreakpointObject;
	Breakpoint rightBreakpointObject;
	int buffer = 20;
	private String mutationType;
	private String leftReference;
	private String rightReference;
	private String leftStrand;
	private String rightStrand;
	private Integer leftBreakpoint;
	private Integer rightBreakpoint;
	private boolean hasClusterMatch = false;
	private boolean hasMatchingBreakpoints;
	private String rightMateStrand;
	private String leftMateStrand;
	private boolean hasClipMatch;
	private boolean oneSide = false;
	private boolean rescuedClips;
	private boolean alreadyMatched;
	private static String TAB = "\t";
	private String orientationCategory = "";


	public SoftClipCluster() {
		
	}

	public SoftClipCluster(Breakpoint leftBreakpoint) throws Exception {
		this.name = leftBreakpoint.getName();
		this.leftBreakpointObject = leftBreakpoint;		
		setStartAndEnd();		
		this.rightBreakpointObject = null;	
		this.oneSide = true;
		this.mutationType = defineMutationType();
	}
	
	public SoftClipCluster(Breakpoint leftBreakpoint, Breakpoint rightBreakpoint) throws Exception {
		setName(leftBreakpoint.getName(), rightBreakpoint.getName());
		this.leftBreakpointObject = leftBreakpoint;
		this.rightBreakpointObject = rightBreakpoint;
		this.hasMatchingBreakpoints = true;
		
		if (leftBreakpoint.getClipsSize() == 0 || rightBreakpoint.getClipsSize() == 0) {
			this.oneSide = true;
		}
		setStartAndEnd();
		this.mutationType = defineMutationType();
	}


	private void setName(String nameOne, String nameTwo) {
		
		if (nameOne.compareTo(nameTwo) > 0) {
			this.name = nameTwo + ":" + nameOne;
		} else {
			this.name = nameOne + ":" + nameTwo;
		}
	}

	private void setStartAndEnd() {
		if (this.leftBreakpointObject == null) {
			this.leftBreakpoint = rightBreakpointObject.getMateBreakpoint();
			this.leftReference = rightBreakpointObject.getMateReference();
			this.leftStrand = rightBreakpointObject.getMateStrand();
			this.rightBreakpoint = rightBreakpointObject.getBreakpoint();			
			this.rightReference = rightBreakpointObject.getReference();
			this.rightStrand = rightBreakpointObject.getStrand();	
			this.rightMateStrand = rightBreakpointObject.getMateStrand();
			this.leftMateStrand = rightBreakpointObject.getStrand();
		} else {
			this.leftBreakpoint = leftBreakpointObject.getBreakpoint();
			this.leftReference = leftBreakpointObject.getReference();
			this.leftStrand = leftBreakpointObject.getStrand();
			this.leftMateStrand = leftBreakpointObject.getMateStrand();
			if (rightBreakpointObject == null) {
				this.rightBreakpoint = leftBreakpointObject.getMateBreakpoint();			
				this.rightReference = leftBreakpointObject.getMateReference();
				this.rightStrand = leftBreakpointObject.getMateStrand();
				this.rightMateStrand = leftBreakpointObject.getStrand();				
			} else {
				this.rightBreakpoint = rightBreakpointObject.getBreakpoint();		
				this.rightReference = rightBreakpointObject.getReference();
				this.rightStrand = rightBreakpointObject.getStrand();	
				this.rightMateStrand = rightBreakpointObject.getMateStrand();
			}
		}		
	}	

	public String defineMutationType() throws Exception {
		
//		if (!leftReference.equals(rightReference)) {
//				return "CTX";
//		}
		if (oneSide) {
			if (!leftReference.equals(rightReference)) {
				checkOrder();
				return "CTX";
			} else {
				String mut = findSingleSideMutationType();
				orientationCategory = "";
				checkOrder();
				return mut;
			}
			
		} else {
				checkOrder();
				return findTwoSidedMutationType();	
			
		}
	}

	public String findTwoSidedMutationType() {
			if (!leftBreakpointObject.isLeft() && rightBreakpointObject.isLeft()) {
			
			if (leftBreakpointObject.getStrand().equals(leftBreakpointObject.getMateStrand()) && rightBreakpointObject.getStrand().equals(rightBreakpointObject.getMateStrand())) {
				orientationCategory = "1";				
				if (!leftReference.equals(rightReference)) {
					return "CTX";
				} else {
					return "DEL/ITX";
				}
			} 				
			
		} else if (leftBreakpointObject.isLeft() && !rightBreakpointObject.isLeft()) {
			if (leftBreakpointObject.getStrand().equals(leftBreakpointObject.getMateStrand()) && rightBreakpointObject.getStrand().equals(rightBreakpointObject.getMateStrand())) {
				orientationCategory = QSVConstants.ORIENTATION_2;				
				if (!leftReference.equals(rightReference)) {
					return "CTX";
				} else {
					return "DUP/INS/ITX";
				}				
			}			
		} else if ((leftBreakpointObject.isLeft() && rightBreakpointObject.isLeft()) ||
				(!leftBreakpointObject.isLeft() && !rightBreakpointObject.isLeft())) {
			if (!leftBreakpointObject.getStrand().equals(leftBreakpointObject.getMateStrand()) && !rightBreakpointObject.getStrand().equals(rightBreakpointObject.getMateStrand())) {
				if (leftBreakpointObject.isLeft() && rightBreakpointObject.isLeft()) {
					orientationCategory = QSVConstants.ORIENTATION_4;
				}
				if (!leftBreakpointObject.isLeft() && !rightBreakpointObject.isLeft()) {
					orientationCategory = QSVConstants.ORIENTATION_3;
				}
				if (!leftReference.equals(rightReference)) {
					return "CTX";
				} else {
					return "INV/ITX";
				}				
			}			
		}
		
		return "ITX";	
	}

	public String findSingleSideMutationType() throws Exception {	
		Breakpoint bpObject = getSingleBreakpoint();

		if (bpObject.isLeft()) {
			if (bpObject.getBreakpoint() < bpObject.getMateBreakpoint()) {
				if (bpObject.getMatchingStrands()) {
					return "DUP/INS/ITX";
				} else {
					return "ITX";
				}
			} else {
				if (bpObject.getMatchingStrands()) {
					return "DEL/ITX";
				} else {
					return "ITX";
				}
			}
			
		} else {
			if (bpObject.getBreakpoint() < bpObject.getMateBreakpoint()) {
				if (bpObject.getMatchingStrands()) {
					return "DEL/ITX";
				} else {
					return "ITX";
				}
			} else {
				if (bpObject.getMatchingStrands()) {
					return "DUP/INS/ITX";
				} else {
					return "ITX";
				}
			}
		}
	}

	public boolean checkOrder() {
		if (leftReference.equals(rightReference)) {
			// wrong order: swap the records
            if (leftBreakpoint.intValue() > rightBreakpoint.intValue()) {
            	swapBreakpoints();
            	return true;
            }

          // on different chromosomes
        } else {
            boolean reorder = QSVUtil.reorderByChromosomes(leftReference, rightReference);
            if (reorder) {
            	swapBreakpoints();
            	return true;
            }
        }
		return false;
	}
	
	public void swapBreakpoints() {
		if (rightBreakpointObject != null) {
			Breakpoint temp = leftBreakpointObject;			
			leftBreakpointObject = rightBreakpointObject;
			rightBreakpointObject = temp;			
		} else {
			this.rightBreakpointObject = this.leftBreakpointObject;
			this.leftBreakpointObject = null;
		}
		setStartAndEnd();
	}

	@Override
	public int compareTo(SoftClipCluster o) {
		return this.name.compareTo(o.getName());
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}	

	public boolean isRescuedClips() {
		return rescuedClips;
	}

	public void setRescuedClips(boolean rescuedClips) {
		this.rescuedClips = rescuedClips;
	}
	
	public boolean isOneSide() {
		return oneSide;
	}

	public void setOneSide(boolean oneSide) {
		this.oneSide = oneSide;
	}
	
	public Breakpoint getLeftBreakpointObject() {
		return leftBreakpointObject;
	}

	public void setLeftBreakpointObject(Breakpoint leftBreakpointClips) {
		this.leftBreakpointObject = leftBreakpointClips;
	}

	public Breakpoint getRightBreakpointObject() {
		return rightBreakpointObject;
	}

	public void setRightBreakpointObject(Breakpoint rightBreakpointClips) {
		this.rightBreakpointObject = rightBreakpointClips;
	}

	public Integer getLeftBreakpoint() {
		return leftBreakpoint;
	}

	public void setLeftBreakpoint(Integer leftBreakpoint) {
		this.leftBreakpoint = leftBreakpoint;
	}

	public Integer getRightBreakpoint() {
		return rightBreakpoint;
	}

	public void setRightBreakpoint(Integer rightBreakpoint) {
		this.rightBreakpoint = rightBreakpoint;
	}
	

	public String getRightReference() {
		return rightReference;
	}

	public void setRightReference(String rightReference) {
		this.rightReference = rightReference;
	}

	public String getLeftReference() {
		return leftReference;
	}

	public void setLeftReference(String leftReference) {
		this.leftReference = leftReference;
	}


	public String getMutationType() {
		return this.mutationType;
	}
	
	public int getLeftBreakpointStart() {
		int bpStart = this.leftBreakpoint;	
		return bpStart;
	}
	
	public int getLeftBreakpointEnd() {
		int bpEnd = this.leftBreakpoint;	
		return bpEnd;
	}
	
	public int getRightBreakpointStart() {
		int bpStart = this.rightBreakpoint;
	
		return bpStart;
	}
	
	public int getRightBreakpointEnd() {
		int bpEnd = this.rightBreakpoint;
	
		return bpEnd;
	}
	
	public void setHasClusterMatch(boolean b) {
		this.hasClusterMatch = b;		
	}

	public boolean hasClusterMatch() {
		return hasClusterMatch;
	}

	public boolean hasMatchingBreakpoints() {
		return hasMatchingBreakpoints;
	}
	
	public boolean findMatchingBreakpoints() {
		if (getLeftBreakpointObject() != null && getRightBreakpointObject() != null) {
			if (getRightBreakpointObject().getClipsSize() > 0 && getLeftBreakpointObject().getClipsSize() > 0) {
				return true;
			}
		}
		return false;
	}

	public boolean hasClipMatch() {
		return this.hasClipMatch;
	}

	public void setHasClipMatch(boolean clipMatch) {
		this.hasClipMatch = clipMatch;	
	}	
	
	public String getOrientationCategory() {
		return orientationCategory;
	}

	public void setOrientationCategory(String orientationCategory) {
		this.orientationCategory = orientationCategory;
	}
	
	@Override
	public boolean equals(Object aThat) {
		if (this == aThat)
			return true;

		if (!(aThat instanceof SoftClipCluster))
			return false;

		SoftClipCluster other = (SoftClipCluster) aThat;

		return this.name.equals(other.getName());
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
        int result = 1;
        result = prime * result
                + ((this.name == null) ? 0 : name.hashCode());
        
        return result;
	}


	public boolean findMatchingBreakpoints(SoftClipCluster compare) throws Exception {
		
		Breakpoint left = getSingleBreakpoint();
		Breakpoint right = compare.getSingleBreakpoint();
		
		if (left != null && right != null) {
			if (left.getReference().equals(right.getMateReference()) && right.getReference().equals(left.getMateReference())) {
				
				Integer leftBPPosition = left.compare(right.getReference(), right.getBreakpoint());
				Integer rightBPPosition = right.compare(left.getReference(), left.getBreakpoint());			
	
				if (leftBPPosition != null && rightBPPosition != null) {
					this.hasMatchingBreakpoints = true;
					return true;
				}
			}
		}
		
		return false;
	}	
	
	public boolean isGermline() {
		if (leftBreakpointObject != null && rightBreakpointObject != null) {
			if (leftBreakpointObject.isGermline() || rightBreakpointObject.isGermline()) {
				return true;
			}
		} else if (leftBreakpointObject != null) {
			if (leftBreakpointObject.isGermline()) {
				return true;
			}
		} else {
			if (rightBreakpointObject.isGermline()) {
				return true;
			}
		} 		
		return false;
	}

	public int getRealRightBreakpoint() {
		int bp = rightBreakpoint;
		if (this.rightBreakpointObject != null) {
			bp = rightBreakpointObject.getBreakpoint();
		} else {
		}
		
		return bp;
	}
	
	public int getRealLeftBreakpoint() {
		int bp = leftBreakpoint;
		if (this.leftBreakpointObject != null) {
			bp = leftBreakpointObject.getBreakpoint();
		} else {
			bp = rightBreakpointObject.getMateBreakpoint();
		}
		return bp;		
	}

	public int getClipCount(boolean isTumour, boolean leftPos) {		
		
		if (leftBreakpointObject != null) {
				if (leftPos && !orientationCategory.equals(QSVConstants.ORIENTATION_2)) {
					return getLeftClipCount(isTumour);								
				} else if (!leftPos && orientationCategory.equals(QSVConstants.ORIENTATION_2)) {
					return getRightClipCount(isTumour);	
				}
		}
		if (rightBreakpointObject != null) {
			if (!leftPos && !orientationCategory.equals(QSVConstants.ORIENTATION_2)) {
				return getRightClipCount(isTumour);								
			} else {
				if (leftPos && orientationCategory.equals(QSVConstants.ORIENTATION_2)) {
					return getLeftClipCount(isTumour);		
				}
			}
		}
		return 0;
	}

	private int getLeftClipCount(boolean isTumour) {
		int leftCount = 0;
		if (isTumour) {					
			leftCount += leftBreakpointObject.getTumourClips().size();
		} else {
			leftCount += leftBreakpointObject.getNormalClips().size();
		}
		return leftCount;
	}
	
	private int getRightClipCount(boolean isTumour) {
		int rightCount = 0;
		if (isTumour) {
			rightCount += rightBreakpointObject.getTumourClips().size();
		} else {
			rightCount += rightBreakpointObject.getNormalClips().size();
		}
		return rightCount;
	}

	public String getStrand() {
		return this.leftStrand + "|" + this.rightStrand;
	}

	public Breakpoint getSingleBreakpoint() throws Exception {
		if (leftBreakpointObject != null && rightBreakpointObject != null) {
			return null;
		} else if (leftBreakpointObject != null ) {
			return this.leftBreakpointObject;
		} else {
			return this.rightBreakpointObject;
		}
	}

	public String getClips(boolean isLeft, boolean isTumour) {
		
		if (isLeft) {
			if (leftBreakpointObject != null) {
				if (!leftBreakpointObject.isRescued()) {
					if (isTumour) {
						return leftBreakpointObject.getTumourClipString();
					} else {
						return leftBreakpointObject.getNormalClipString();
					}
				}
			}
		} else {
			if (rightBreakpointObject != null) {
				if (!rightBreakpointObject.isRescued()) {
					if (isTumour) {
						return rightBreakpointObject.getTumourClipString();
					} else {
						return rightBreakpointObject.getNormalClipString();
					}
				}
			}
		}
		return "";
	}
	
	public String getLowConfClips(boolean isLeft, boolean isTumour) {
		if (isLeft) {
			if (leftBreakpointObject != null) {
				if (leftBreakpointObject.isRescued()) {
					if (isTumour) {
						return leftBreakpointObject.getTumourClipString();
					} else {
						return leftBreakpointObject.getNormalClipString();
					}
				}
			}
		} else {
			if (rightBreakpointObject != null) {
				if (rightBreakpointObject.isRescued()) {
					if (isTumour) {
						return rightBreakpointObject.getTumourClipString();
					} else {
						return rightBreakpointObject.getNormalClipString();
					}
				}
			}
		}
		return "";
	}
	
	public String getFullName() {
		return this.name + TAB + leftReference + TAB + leftBreakpoint + TAB + leftStrand + TAB + rightReference + TAB + rightBreakpoint + TAB + leftMateStrand + TAB;
	}

	public String getLeftInfo() {
		int size = 0;
		if (leftBreakpointObject != null) {
			size = leftBreakpointObject.getClipsSize(); 
		}
		return this.name + TAB + leftBreakpoint + TAB + leftStrand + TAB + rightBreakpoint + TAB + leftMateStrand + TAB + size;
	}
	
	public String getRightInfo() {
		int size = 0;
		if (rightBreakpointObject != null) {
			size = rightBreakpointObject.getClipsSize(); 
		}
		return this.name + TAB + rightBreakpoint + TAB + rightStrand + TAB + rightMateStrand + TAB + size; 
	}

	public void rescueClips(QSVParameters p, BLAT blat, File tumourFile, File normalFile, String softclipDir, int consensusLength, int chrBuffer, Integer minInsertSize) throws Exception {
		
		Map<Integer, Breakpoint> leftMap = new TreeMap<Integer, Breakpoint>();
		Map<Integer, Breakpoint> rightMap = new TreeMap<Integer, Breakpoint>();
		TreeMap<Integer, List<UnmappedRead>> splitReads = new TreeMap<Integer, List<UnmappedRead>>();
		Integer bp = getOrphanBreakpoint();
		String ref = getOrphanReference();
			
		
		if (bp != null) {
			readRescuedClips(tumourFile, true, ref, bp, leftMap, rightMap, splitReads, consensusLength, chrBuffer, minInsertSize, p.getReadGroupIds(), p.getPairingType());
			readRescuedClips(normalFile, false, ref, bp, leftMap, rightMap, splitReads, consensusLength, chrBuffer, minInsertSize, p.getReadGroupIds(), p.getPairingType());
		}		
		findMaxRescueBreakpoint(p, blat, leftMap, rightMap, splitReads, softclipDir);
	}
	
	public void findMaxRescueBreakpoint(QSVParameters p, BLAT blat,
			Map<Integer, Breakpoint> leftMap,
			Map<Integer, Breakpoint> rightMap, TreeMap<Integer, List<UnmappedRead>> splitReads, String softclipDir) throws Exception {

		//find maximum breakpoint
		Breakpoint maxLengthBp = null;
		int maxLength = 0;
		int buffer = p.getUpperInsertSize() + 100;		

		for (Entry<Integer, Breakpoint> entry: leftMap.entrySet()) {
			Breakpoint b = entry.getValue();
			
			if (b.getMaxBreakpoint(buffer, splitReads, maxLength)) {
				if (b.getBreakpoint().intValue() != this.getSingleBreakpoint().getBreakpoint().intValue()) {
					
					maxLengthBp = b;
					maxLength = b.getMateConsensus().length();
				}
			}
		}
		
		for (Entry<Integer, Breakpoint> entry: rightMap.entrySet()) {
			Breakpoint b = entry.getValue();	
			
			if (b.getMaxBreakpoint(buffer, splitReads, maxLength)) {
				if (b.getBreakpoint().intValue() != this.getSingleBreakpoint().getBreakpoint().intValue()) {
					maxLengthBp = b;
					maxLength = b.getMateConsensus().length();
				}
			}
		}

		if (maxLengthBp != null) {			
			
			if (maxLengthBp.getMateConsensus().length() > 20) {			
				boolean match = maxLengthBp.findRescuedMateBreakpoint(blat, p, softclipDir);
				
				if (match) {
					if (this.getSingleBreakpoint() != null) {
						if (this.findMatchingBreakpoints(new SoftClipCluster(maxLengthBp))) {
							maxLengthBp.setRescued(false);
							
							if (leftBreakpointObject == null) {
								leftBreakpointObject = maxLengthBp;								
							} else {
								rightBreakpointObject = maxLengthBp;	
							}
							
							rescuedClips = true;
							oneSide = false;
							setStartAndEnd();
							this.mutationType = defineMutationType();
							hasMatchingBreakpoints = true;
						}
					}
					
				}
			} 
		}
	}	

	String getOrphanReference() {
		if (leftBreakpointObject == null) {
			//find potential matches for breakpoint
		    return leftReference;
		}    
	
		if (rightBreakpointObject == null) {
			return rightReference;
		}
		return null;
	}

	private void readRescuedClips(File file, boolean isTumour, String reference, int bp, Map<Integer, Breakpoint> leftMap, Map<Integer, Breakpoint> rightMap, TreeMap<Integer, 
			List<UnmappedRead>> splitReads, int consensusLength, int chrBuffer, Integer minInsertSize, List<String> readGroupIds, String pairingType) {
		int bpStart = bp - chrBuffer;
		int bpEnd = bp + chrBuffer;
		
		int referenceStart = bpStart - 120;
		int referenceEnd = bpEnd + 120;
		int count = 0;
		
		SAMFileReader.setDefaultValidationStringency(SAMFileReader.ValidationStringency.SILENT);  
        SAMFileReader reader = SAMFileReaderFactory.createSAMFileReader(file, "silent");
        SAMRecordIterator iter = reader.queryOverlapping(reference, referenceStart, referenceEnd);
        
        	while (iter.hasNext()) {
		        	SAMRecord r = iter.next();	
		        	
		        	if (readGroupIds.contains(r.getReadGroup().getId())) {
		        		if (r.getReadUnmappedFlag()) {
			        		count++;
			        		if (count > 5000) {
			    	        	break;
			    	        }
			        		UnmappedRead splitRead = new UnmappedRead(r, isTumour);
							if (splitReads.containsKey(splitRead.getBpPos())) {
								splitReads.get(splitRead.getBpPos()).add(splitRead);
							} else {
								List<UnmappedRead> reads = new ArrayList<UnmappedRead>();
								reads.add(splitRead);
								splitReads.put(splitRead.getBpPos(), reads);
							}
			        	} else {
				        	if (!r.getDuplicateReadFlag() && r.getCigarString().contains("S")) {
				        		count++;
				        		if (count > 5000) {
				    	        	break;
				    	        }
				        		Clip c = SoftClipStaticMethods.createSoftClipRecord(r, bpStart, bpEnd, reference);
				        		if (c != null) {	        			
			        				if (c.isLeft()) {
			        					if (leftMap.containsKey(c.getBpPos())) {
			        						if (isTumour) {
			        							leftMap.get(c.getBpPos()).addTumourClip(c);
			        						} else {
			        							leftMap.get(c.getBpPos()).addNormalClip(c);
			        						}
				        				} else {
				        					Breakpoint b = new Breakpoint(c.getBpPos(), reference, true, consensusLength, minInsertSize);
				        					b.addTumourClip(c);
				        					leftMap.put(c.getBpPos(), b);
				        				}
			        				} else {
			        					if (rightMap.containsKey(c.getBpPos())) {	        						
			        						if (isTumour) {
			        							rightMap.get(c.getBpPos()).addTumourClip(c);
			        						} else {
			        							rightMap.get(c.getBpPos()).addNormalClip(c);
			        						}
				        				} else {
				        					Breakpoint b = new Breakpoint(c.getBpPos(), reference, false, consensusLength, minInsertSize);
				        					b.addTumourClip(c);
				        					rightMap.put(c.getBpPos(), b);
				        				}
			        				}	        				
				        		}
				        	}
			        	}    		
		        		
		        	}	        	
		        		        	
	        	}        	
    	reader.close();  	
	}

	public Integer getOrphanBreakpoint() throws Exception {
		if (leftBreakpointObject == null) {
			//find potential matches for breakpoint
		    return leftBreakpoint;
		} else if (rightBreakpointObject == null) {
			return rightBreakpoint;
		} else {
			throw new Exception("Null breakpoint");
		}
	}

	public boolean alreadyMatched() {
		return this.alreadyMatched;
	}
	
	public void setAlreadyMatched(boolean b) {
		this.alreadyMatched = b;
	}

	public String getSoftClipConsensusString(String svId) {
		String tab = "\t";
		return svId + tab + leftReference + tab + leftBreakpoint + tab + getLeftSoftClipConsensus() 
				+ tab + rightReference + tab + rightBreakpoint + tab + getRightSoftClipConsensus(); 
	}

	private String getLeftSoftClipConsensus() {
		if (leftBreakpointObject == null) {
			return "\t\t\t\t\t\t\t\t";
		} else {
			return leftBreakpointObject.getContigInfo();
		}
	}
	
	private String getRightSoftClipConsensus() {
		if (rightBreakpointObject == null) {
			return "\t\t\t\t\t\t\t\t";
		} else {
			return rightBreakpointObject.getContigInfo();
		}
	}
	
	public String getMicrohomology(String clusterCategory) throws Exception {
		String nonTemp = getNonTemplateSequence(clusterCategory);
		if (nonTemp.equals(QSVConstants.UNTESTED)) {
			return QSVConstants.UNTESTED;
		} else {
			if (nonTemp.length() > 0 && !nonTemp.equals(QSVConstants.NOT_FOUND)) {
				return QSVConstants.NOT_FOUND;
			}
		}
		
		String cat = null;
		
		if (orientationCategory != null) {
			if (!orientationCategory.equals("")) {
				cat = orientationCategory;
			}
		} else {
			if (clusterCategory != null) {
				cat = clusterCategory;
			}
		}

		String leftReferenceSeq = null;
		String rightReferenceSeq = null;
		Integer leftBp = null;
		Integer rightBp = null;
		String leftStrand = null;
		String rightStrand = null;
		
		if (cat != null) {
			
		if (leftBreakpointObject == null) {
			leftReferenceSeq = rightBreakpointObject.getMateConsensus();
			rightReferenceSeq = rightBreakpointObject.getBreakpointConsenus();
			leftBp = rightBreakpointObject.getMateBreakpoint();
			rightBp = rightBreakpointObject.getBreakpoint();
			//leftStrand = rightBreakpointObject.getMateStrand();
			//rightStrand= rightBreakpointObject.getStrand();
			return "";
		} else if (rightBreakpointObject == null) {
			leftReferenceSeq = leftBreakpointObject.getBreakpointConsenus();
			rightReferenceSeq = leftBreakpointObject.getMateConsensus();
			leftBp = leftBreakpointObject.getBreakpoint();
			rightBp = leftBreakpointObject.getMateBreakpoint();
			//leftStrand = leftBreakpointObject.getStrand();
			//rightStrand= leftBreakpointObject.getMateStrand();
			return "";
		} else {
			leftReferenceSeq = leftBreakpointObject.getBreakpointConsenus();
			rightReferenceSeq = rightBreakpointObject.getBreakpointConsenus();
			leftBp = leftBreakpointObject.getBreakpoint();
			rightBp = rightBreakpointObject.getBreakpoint();
			//leftStrand = leftBreakpointObject.getStrand();
			//rightStrand= rightBreakpointObject.getStrand();
		}
	
			if (cat.equals(QSVConstants.ORIENTATION_2)) {
				String tmp = leftReferenceSeq;			
				leftReferenceSeq = rightReferenceSeq;
				rightReferenceSeq = tmp;		
				Integer tmpInt = leftBp;
				leftBp = rightBp;
				rightBp = tmpInt;
			} else if (cat.equals(QSVConstants.ORIENTATION_3)) {
				rightReferenceSeq = QSVUtil.reverseComplement(rightReferenceSeq);
			} else if (cat.equals(QSVConstants.ORIENTATION_4)) {
				leftReferenceSeq = QSVUtil.reverseComplement(leftReferenceSeq);
			}
			
			String mh = "";
			
			
			for (int i=1; i<rightReferenceSeq.length(); i++) {
				//startPosition
				String currentRight = rightReferenceSeq.substring(0, i);
				
				if (leftReferenceSeq.endsWith(currentRight)) {
					if (currentRight.length() > mh.length()) {
						mh = currentRight;
					}
				}			
			}
			
			if (mh.equals("")) {
				return QSVConstants.NOT_FOUND;
			} else {
//				if (needToReverseComplement(cat)) {
//					return QSVUtil.reverseComplement(mh);
//				} else {
				return mh;
//				}
			}
		} else {
			return QSVConstants.UNTESTED;
		}		
	}
	
	private boolean needToReverseComplement(String cat) {
		if (hasMatchingBreakpoints) {
			if (cat.equals(QSVConstants.ORIENTATION_3)) {
				if (leftBreakpointObject.getStrand().equals("-")) {
					return true;
				}
			}
			if (cat.equals(QSVConstants.ORIENTATION_4)) {
				if (leftBreakpointObject.getStrand().equals("+")) {
					return true;
				}
			}
		}		
		return false;
	}

	public String getOverlappingContigSequence() throws Exception {		
		
		if (leftBreakpointObject == null) {
			return rightBreakpointObject.getCompleteConsensus();
		} else if (rightBreakpointObject == null) {
			return leftBreakpointObject.getCompleteConsensus();
		} else {
			QSVAssemble a = new QSVAssemble();		
			return a.getFinalClipContig(leftBreakpointObject.getCompleteConsensus(), rightBreakpointObject.getCompleteConsensus());	
		}
	}
	
	public int getConsensusSplits(boolean isLeft) {
		if (isLeft && leftBreakpointObject != null) {
			return leftBreakpointObject.getSplitConsensusReads();
		}
		if (!isLeft && rightBreakpointObject != null) {			
			return rightBreakpointObject.getSplitConsensusReads();
		}
		return 0;
	}
	
	public int getSplitReadTotal(boolean isLeft) {
		if (isLeft && leftBreakpointObject != null) {			
			return leftBreakpointObject.getSplitReadsSize();		
		}
		if (!isLeft && rightBreakpointObject != null) {
			return rightBreakpointObject.getSplitReadsSize();
		}
		return 0;
	}

	public int getConsensusClips(boolean isLeft) {
		if (isLeft && leftBreakpointObject != null) {
			return leftBreakpointObject.getClipConsensusReads();
		}
		if (!isLeft && rightBreakpointObject != null) {
			return rightBreakpointObject.getClipConsensusReads();
		}
		return 0;
	}
	
	public String getBreakpointConsensus(boolean isLeft) {
		if (isLeft) {
			if (leftBreakpointObject != null) {
				return leftBreakpointObject.getMateConsensus();
			}
		} else {
			if (rightBreakpointObject != null) {
				return rightBreakpointObject.getMateConsensus();
			}
		}
		return "";
	}

	public int getClipSize(boolean isLeft) {
		if (isLeft && leftBreakpointObject != null) {
			return leftBreakpointObject.getTumourClips().size() + leftBreakpointObject.getNormalClips().size();
		}
		if (!isLeft && rightBreakpointObject != null) {
			return rightBreakpointObject.getTumourClips().size() + rightBreakpointObject.getNormalClips().size();
		}
		return 0;
	}

	public String getNonTemplateSequence(String clusterCategory) {
		String nonTmp = QSVConstants.UNTESTED;
		if (hasMatchingBreakpoints) {
			
			String cat = null;
			
			if (orientationCategory != null) {
				if (!orientationCategory.equals("")) {
					cat = orientationCategory;
				}
			} else {
				if (clusterCategory != null) {
					cat = clusterCategory;
				}
			}
			
			if (cat != null) {
				
				String leftClipSeq = rightBreakpointObject.getMateConsensusPosStrand();			
				String rightClipSeq = leftBreakpointObject.getMateConsensusPosStrand();
				int leftNonTemp = rightBreakpointObject.getNonTempBases();
				int rightNonTemp = leftBreakpointObject.getNonTempBases();
				
				if (cat.equals(QSVConstants.ORIENTATION_2)) {
					String tmp = leftClipSeq;			
					leftClipSeq = rightClipSeq;
					rightClipSeq = tmp;		
				}
				
				if (leftNonTemp == rightNonTemp) {
					if (leftNonTemp == 0) {
						nonTmp = "";
					}
					String currentRight = rightClipSeq.substring(0, rightNonTemp);
					if (leftClipSeq.endsWith(currentRight)) {
						nonTmp = currentRight;
					}
				}
			}
		}
		return nonTmp;
	}

	public boolean matchesSplitReadBreakpoints(String splitLeftReference,
			String splitRightReference, int splitLeftBreakpoint, int splitRightBreakpoint) {
		
		if (orientationCategory.equals(QSVConstants.ORIENTATION_2)) {
			if (leftReference.equals(splitRightReference) && rightReference.equals(splitLeftReference) &&
					leftBreakpoint == splitRightBreakpoint && rightBreakpoint == splitLeftBreakpoint) {
				return true;
			}				
				
		} else {
			if (leftReference.equals(splitLeftReference) && rightReference.equals(splitRightReference) &&
					leftBreakpoint == splitLeftBreakpoint && rightBreakpoint == splitRightBreakpoint) {
				return true;
			}
		}
		
		return false;
	}
}
