/**
 * © Copyright The University of Queensland 2010-2014.
 * © Copyright QIMR Berghofer Medical Research Institute 2014-2016.
 *
 * This code is released under the terms outlined in the included LICENSE file.
 */
package org.qcmg.picard;

import java.util.Comparator;

import htsjdk.samtools.SAMRecordCoordinateComparator;

public final class SAMRecordWrapperComparator implements
		Comparator<SAMRecordWrapper> {
	private final SAMRecordCoordinateComparator comparator = new SAMRecordCoordinateComparator();

	public int compare(final SAMRecordWrapper o1, final SAMRecordWrapper o2) {
		return comparator.compare(o1.getRecord(), o2.getRecord());
	}
}
