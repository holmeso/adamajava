/**
 * © Copyright The University of Queensland 2010-2014.  This code is released under the terms outlined in the included LICENSE file.
 */
package org.qcmg.ensembl.model;


// default package
// Generated May 9, 2011 1:21:58 PM by Hibernate Tools 3.3.0.GA

import static javax.persistence.GenerationType.IDENTITY;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * Transcript generated by hbm2java
 */
@Entity
@Table(name = "homo_sapiens_core_55_37.transcript")
public class Transcript implements java.io.Serializable {

	private static final long serialVersionUID = 2229835506083476775L;
	
	private Integer transcriptId;
	private Gene gene;
	private short analysisId;
	private int seqRegionId;
	private int seqRegionStart;
	private int seqRegionEnd;
	private byte seqRegionStrand;
	private Integer displayXrefId;
	private String biotype;
	private String status;
	private String description;
	private boolean isCurrent;

	public Transcript() {
	}

	public Transcript(short analysisId, int seqRegionId, int seqRegionStart,
			int seqRegionEnd, byte seqRegionStrand, String biotype,
			boolean isCurrent) {
		this.analysisId = analysisId;
		this.seqRegionId = seqRegionId;
		this.seqRegionStart = seqRegionStart;
		this.seqRegionEnd = seqRegionEnd;
		this.seqRegionStrand = seqRegionStrand;
		this.biotype = biotype;
		this.isCurrent = isCurrent;
	}

	public Transcript(Gene gene, short analysisId, int seqRegionId,
			int seqRegionStart, int seqRegionEnd, byte seqRegionStrand,
			Integer displayXrefId, String biotype, String status,
			String description, boolean isCurrent) {
		this.gene = gene;
		this.analysisId = analysisId;
		this.seqRegionId = seqRegionId;
		this.seqRegionStart = seqRegionStart;
		this.seqRegionEnd = seqRegionEnd;
		this.seqRegionStrand = seqRegionStrand;
		this.displayXrefId = displayXrefId;
		this.biotype = biotype;
		this.status = status;
		this.description = description;
		this.isCurrent = isCurrent;
	}

	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "transcript_id", unique = true, nullable = false)
	public Integer getTranscriptId() {
		return this.transcriptId;
	}

	public void setTranscriptId(Integer transcriptId) {
		this.transcriptId = transcriptId;
	}

	@ManyToOne(cascade = CascadeType.ALL)
	@JoinColumn(name="gene_id")
	public Gene getGene() {
		return this.gene;
	}

	public void setGene(Gene gene) {
		this.gene = gene;
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

	@Column(name = "biotype", nullable = false, length = 40)
	public String getBiotype() {
		return this.biotype;
	}

	public void setBiotype(String biotype) {
		this.biotype = biotype;
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

}
