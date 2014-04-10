/**
 * © Copyright The University of Queensland 2010-2014.  This code is released under the terms outlined in the included LICENSE file.
 */
package org.qcmg.ensembl.model;


// default package
// Generated May 9, 2011 1:21:58 PM by Hibernate Tools 3.3.0.GA

import static javax.persistence.GenerationType.IDENTITY;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Gene generated by hbm2java
 */
@Entity
@Table(name = "homo_sapiens_core_55_37.gene")
public class Gene implements java.io.Serializable {

	private Integer geneId;
	private String biotype;
	private short analysisId;
	private int seqRegionId;
	private int seqRegionStart;
	private int seqRegionEnd;
	private byte seqRegionStrand;
	private Integer displayXrefId;
	private String source;
	private String status;
	private String description;
	private boolean isCurrent;
	private int canonicalTranscriptId;
	private String canonicalAnnotation;

	public Gene() {
	}

	public Gene(String biotype, short analysisId, int seqRegionId,
			int seqRegionStart, int seqRegionEnd, byte seqRegionStrand,
			String source, boolean isCurrent, int canonicalTranscriptId) {
		this.biotype = biotype;
		this.analysisId = analysisId;
		this.seqRegionId = seqRegionId;
		this.seqRegionStart = seqRegionStart;
		this.seqRegionEnd = seqRegionEnd;
		this.seqRegionStrand = seqRegionStrand;
		this.source = source;
		this.isCurrent = isCurrent;
		this.canonicalTranscriptId = canonicalTranscriptId;
	}

	public Gene(String biotype, short analysisId, int seqRegionId,
			int seqRegionStart, int seqRegionEnd, byte seqRegionStrand,
			Integer displayXrefId, String source, String status,
			String description, boolean isCurrent, int canonicalTranscriptId,
			String canonicalAnnotation) {
		this.biotype = biotype;
		this.analysisId = analysisId;
		this.seqRegionId = seqRegionId;
		this.seqRegionStart = seqRegionStart;
		this.seqRegionEnd = seqRegionEnd;
		this.seqRegionStrand = seqRegionStrand;
		this.displayXrefId = displayXrefId;
		this.source = source;
		this.status = status;
		this.description = description;
		this.isCurrent = isCurrent;
		this.canonicalTranscriptId = canonicalTranscriptId;
		this.canonicalAnnotation = canonicalAnnotation;
	}

	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "gene_id", unique = true, nullable = false)
	public Integer getGeneId() {
		return this.geneId;
	}

	public void setGeneId(Integer geneId) {
		this.geneId = geneId;
	}

	@Column(name = "biotype", nullable = false, length = 40)
	public String getBiotype() {
		return this.biotype;
	}

	public void setBiotype(String biotype) {
		this.biotype = biotype;
	}

	@Column(name = "analysis_id", nullable = false)
	public short getAnalysisId() {
		return this.analysisId;
	}

	public void setAnalysisId(short analysisId) {
		this.analysisId = analysisId;
	}

	@Column(name = "seq_region_id", nullable = false)
	public int getSeqRegionId() {
		return this.seqRegionId;
	}

	public void setSeqRegionId(int seqRegionId) {
		this.seqRegionId = seqRegionId;
	}

	@Column(name = "seq_region_start", nullable = false)
	public int getSeqRegionStart() {
		return this.seqRegionStart;
	}

	public void setSeqRegionStart(int seqRegionStart) {
		this.seqRegionStart = seqRegionStart;
	}

	@Column(name = "seq_region_end", nullable = false)
	public int getSeqRegionEnd() {
		return this.seqRegionEnd;
	}

	public void setSeqRegionEnd(int seqRegionEnd) {
		this.seqRegionEnd = seqRegionEnd;
	}

	@Column(name = "seq_region_strand", nullable = false)
	public byte getSeqRegionStrand() {
		return this.seqRegionStrand;
	}

	public void setSeqRegionStrand(byte seqRegionStrand) {
		this.seqRegionStrand = seqRegionStrand;
	}

	@Column(name = "display_xref_id")
	public Integer getDisplayXrefId() {
		return this.displayXrefId;
	}

	public void setDisplayXrefId(Integer displayXrefId) {
		this.displayXrefId = displayXrefId;
	}

	@Column(name = "source", nullable = false, length = 20)
	public String getSource() {
		return this.source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	@Column(name = "status", length = 19)
	public String getStatus() {
		return this.status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	@Column(name = "description", length = 65535)
	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	@Column(name = "is_current", nullable = false)
	public boolean isIsCurrent() {
		return this.isCurrent;
	}

	public void setIsCurrent(boolean isCurrent) {
		this.isCurrent = isCurrent;
	}

	@Column(name = "canonical_transcript_id", nullable = false)
	public int getCanonicalTranscriptId() {
		return this.canonicalTranscriptId;
	}

	public void setCanonicalTranscriptId(int canonicalTranscriptId) {
		this.canonicalTranscriptId = canonicalTranscriptId;
	}

	@Column(name = "canonical_annotation")
	public String getCanonicalAnnotation() {
		return this.canonicalAnnotation;
	}

	public void setCanonicalAnnotation(String canonicalAnnotation) {
		this.canonicalAnnotation = canonicalAnnotation;
	}

}