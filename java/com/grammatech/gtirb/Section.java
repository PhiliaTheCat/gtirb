/*
 *  Copyright (C) 2020 GrammaTech, Inc.
 *
 *  This code is licensed under the MIT license. See the LICENSE file in the
 *  project root for license terms.
 *
 *  This project is sponsored by the Office of Naval Research, One Liberty
 *  Center, 875 N. Randolph Street, Arlington, VA 22203 under contract #
 *  N68335-17-C-0700.  The content of the information does not necessarily
 *  reflect the position or policy of the Government and no official
 *  endorsement should be inferred.
 *
 */

package com.grammatech.gtirb;

import com.grammatech.gtirb.proto.ByteIntervalOuterClass;
import com.grammatech.gtirb.proto.SectionOuterClass;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The Section class represents a named section or segment of a program file,
 * with flags that define its settings and ByteIntervals to store binary
 * information.
 */
public class Section extends Node implements TreeListItem {

    /**
     * Idenfities the flags used for a section.
     */
    public enum SectionFlag {
        Undefined,
        Readable,
        Writable,
        Executable,
        Loaded,
        Initialized,
        ThreadLocal
    }

    private String name;
    private TreeMap<Long, List<ByteInterval>> byteIntervalTree;
    private List<SectionFlag> sectionFlags;
    private SectionOuterClass.Section protoSection;
    private Module module;

    /**
     * Class constructor for a Section from a protobuf section.
     * @param  protoSection  The section as serialized into a protocol buffer.
     * @param  module        The Module that owns this Section.
     */
    public Section(SectionOuterClass.Section protoSection, Module module) {

        this.protoSection = protoSection;
        UUID myUuid = Util.byteStringToUuid(protoSection.getUuid());
        super.setUuid(myUuid);
        this.name = protoSection.getName();
        this.module = module;

        byteIntervalTree = new TreeMap<Long, List<ByteInterval>>();
        List<ByteIntervalOuterClass.ByteInterval> protoByteIntervalList =
            protoSection.getByteIntervalsList();
        for (ByteIntervalOuterClass.ByteInterval protoByteInterval :
             protoByteIntervalList) {
            ByteInterval byteInterval =
                new ByteInterval(protoByteInterval, this);
            TreeListUtils.insertItem(byteInterval, byteIntervalTree);
        }

        this.sectionFlags = new ArrayList<SectionFlag>();
        for (Integer value : protoSection.getSectionFlagsValueList()) {
            SectionFlag newSectionFlag = SectionFlag.values()[value];
            this.sectionFlags.add(newSectionFlag);
        }
    }

    /**
     * Class Constructor.
     * @param  name            The name of this Section.
     * @param  flags           A list of flags to apply to this Section.
     * @param  byteIntervals   A list of ByteIntervals belonging to this
     * Section.
     * @param  module          The Module that owns this Section.
     */
    public Section(String name, List<SectionFlag> flags,
                   List<ByteInterval> byteIntervals, Module module) {
        this.protoSection = null;
        this.uuid = UUID.randomUUID();
        this.setName(name);
        this.setSectionFlags(flags);
        this.setModule(module);
        byteIntervalTree = new TreeMap<Long, List<ByteInterval>>();
        for (ByteInterval byteInterval : byteIntervals)
            TreeListUtils.insertItem(byteInterval, byteIntervalTree);
    }

    /**
     * Get the name of a {@link Section Section}.
     *
     * @return  The section name.
     */
    public String getName() { return this.name; }

    /**
     * Set the name of this Section.
     *
     * @param name    The section name.
     */
    public void setName(String name) { this.name = name; }

    /**
     * Get the {@link Module} this Section belongs to.
     *
     * @return  The Module that this Section belongs to, or null if it
     * does not belong to any Module.
     */
    public Module getModule() { return this.module; }

    /**
     * Set the Module this Section belongs to.
     *
     * @param module    The Module that this Section belongs to, or null if it
     * does not belong to any Module.
     */
    public void setModule(Module module) { this.module = module; }

    /**
     * Get a ByteInterval iterator.
     *
     * @return  An iterator for iterating through all the ByteIntervals in this
     * Section.
     */
    public Iterator<ByteInterval> getByteIntervalIterator() {
        TreeListUtils<ByteInterval> byteIntervalTreeIterator =
            new TreeListUtils<ByteInterval>(this.byteIntervalTree);
        return byteIntervalTreeIterator.iterator();
    }

    /**
     * Get the ByteIntervals belonging to this Section.
     *
     * @return  A list of ByteIntervals belonging to this Section.
     */
    public List<ByteInterval> getByteIntervals() {
        List<ByteInterval> resultList = new ArrayList<ByteInterval>();
        Iterator<ByteInterval> byteIntervals = this.getByteIntervalIterator();
        while (byteIntervals.hasNext())
            resultList.add(byteIntervals.next());
        return resultList;
    }

    /**
     * Set the Sections's ByteInterval list.
     *
     * @param byteIntervals    A list of ByteIntervals that will belong to this
     * Section.
     */
    public void setByteIntervals(List<ByteInterval> byteIntervals) {
        // Make sure to start with an empty tree
        this.byteIntervalTree.clear();
        // Add all the new byte intervals
        for (ByteInterval byteInterval : byteIntervals) {
            TreeListUtils.insertItem(byteInterval, byteIntervalTree);
        }
    }

    /**
     * Get the flags applying to this Section.
     *
     * @return  A list of flags applying to this Section.
     */
    public List<SectionFlag> getSectionFlags() { return sectionFlags; }

    /**
     * Set the Sections's flag list.
     *
     * @param sectionFlags    A list of flags that will be applied to this
     * Section.
     */
    public void setSectionFlags(List<SectionFlag> sectionFlags) {
        this.sectionFlags = sectionFlags;
    }

    /**
     * Return the size of this section, if known.
     *
     * The size is calculated from the {@link ByteInterval} objects in this
     * section. More specifically, if the address of all byte intervals in this
     * section are fixed, then it will return the difference between the lowest
     * and highest address among the intervals. If any one interval does not
     * have an address, then this function will return null, as the size is not
     * calculable in that case. Note that a section with no intervals in it has
     * no address or size, so it will return null in that case.
     *
     * @return The size of this section if known, otherwise null.
     */
    public long getSize() {
        if (byteIntervalTree.size() == 0)
            return 0;
        Iterator<ByteInterval> byteIntervalTreeIterator =
            this.getByteIntervalIterator();
        // Verify all ByteIntervals have an address
        Long startAddress = 0L;
        Long endAddress = 0L;
        while (byteIntervalTreeIterator.hasNext()) {
            ByteInterval byteInterval = byteIntervalTreeIterator.next();
            if (byteInterval.hasAddress() == false)
                return 0;
            if (startAddress == 0L)
                startAddress = byteInterval.getAddress();
            endAddress = byteInterval.getAddress() + byteInterval.getSize();
        }
        // Return the difference between start of first and end of last byte
        // interval
        return (endAddress - startAddress);
    }

    /**
     * Return the address of this section, if known.
     *
     * The address is calculated from the {@link ByteInterval} objects in this
     * section. More specifically, if the address of all byte intervals in this
     * section are fixed, then it will return the address of the interval lowest
     * in memory. If any one interval does not have an address, then this
     * function will return null, as the address is not calculable in that case.
     * Note that a section with no intervals in it has no address or size, so it
     * will return null in that case.
     *
     * @return The address of this section if known, otherwise null.
     */
    public Long getAddress() {
        if (byteIntervalTree.size() == 0)
            return null;
        Iterator<ByteInterval> byteIntervalTreeIterator =
            this.getByteIntervalIterator();
        // Verify all ByteIntervals have an address
        Long startAddress = 0L;
        while (byteIntervalTreeIterator.hasNext()) {
            ByteInterval byteInterval = byteIntervalTreeIterator.next();
            if (byteInterval.hasAddress() == false)
                return null;
            if (startAddress == 0L)
                startAddress = byteInterval.getAddress();
        }
        // Return the start of the first byte interval
        return (startAddress);
    }

    /**
     * Get the index to manage this Section with.
     *
     * This index is used for storing and retrieving the Section, as
     * required by the TreeListItem interface. Sections are ordered by
     * address, so this method just returns the address.
     * @return  The ByteInterval index, which is it's address.
     */
    public long getIndex() {
        Long address = this.getAddress();
        if (address == null)
            return 0;
        return address;
    }

    /**
     * Get the original protobuf of this {@link Section}.
     *
     * @return The protobuf the section was imported from, or
     * null of it was not imported from a protobuf.
     */
    public SectionOuterClass.Section getProtoSection() {
        return this.protoSection;
    }

    /**
     * Get all ByteIntervals containing an address.
     *
     * @param address      The address to look for.
     * @return             A list of ByteIntervals that intersect this address,
     * or empty list if none.
     */
    public List<ByteInterval> findByteIntervalsOn(long address) {
        return TreeListUtils.getItemsIntersectingIndex(address,
                                                       this.byteIntervalTree);
    }

    /**
     * Get all ByteIntervals containing any address in a range.
     *
     * @param startAddress      The beginning of the address range to look for.
     * @param endAddress        The last address of the address range to look
     * for.
     * @return                  A list of ByteIntervals that intersect this
     * address range, or empty list if none.
     */
    public List<ByteInterval> findByteIntervalsOn(long startAddress,
                                                  long endAddress) {
        return TreeListUtils.getItemsIntersectingIndexRange(
            startAddress, endAddress, this.byteIntervalTree);
    }

    /**
     * Get all ByteIntervals that begin at an address.
     *
     * @param address      The address to look for.
     * @return             A list of ByteIntervals that that start at this
     * address, or null if none.
     */
    public List<ByteInterval> findByteIntervalsAt(long address) {
        return TreeListUtils.getItemsAtStartIndex(address,
                                                  this.byteIntervalTree);
    }

    /**
     * Get all ByteIntervals that begin at a range of addressees.
     *
     * @param startAddress      The beginning of the address range to look for.
     * @param endAddress        The last address in the address to look for.
     * @return                  A list of ByteIntervals that that start at this
     * address, or null if none.
     */
    public List<ByteInterval> findByteIntervalsAt(long startAddress,
                                                  long endAddress) {
        return TreeListUtils.getItemsAtStartIndexRange(startAddress, endAddress,
                                                       this.byteIntervalTree);
    }

    /**
     * De-serialize a {@link Section} from a protobuf .
     *
     * @return An initialized section.
     */
    public static Section fromProtobuf(SectionOuterClass.Section protoSection,
                                       Module module) {
        return new Section(protoSection, module);
    }

    /**
     * Serialize this Section into a protobuf .
     *
     * @return Section protocol buffer.
     */
    public SectionOuterClass.Section.Builder toProtobuf() {
        SectionOuterClass.Section.Builder protoSection =
            SectionOuterClass.Section.newBuilder();
        protoSection.setUuid(Util.uuidToByteString(this.getUuid()));
        protoSection.setName(this.getName());

        // Add byte intervals
        Iterator<ByteInterval> byteIntervals = this.getByteIntervalIterator();
        while (byteIntervals.hasNext()) {
            ByteInterval byteInterval = byteIntervals.next();
            ByteIntervalOuterClass.ByteInterval.Builder protoByteInterval =
                byteInterval.toProtobuf();
            protoSection.addByteIntervals(protoByteInterval);
        }

        for (SectionFlag sectionFlag : this.sectionFlags) {
            //            SectionOuterClass.SectionFlag protoSectionFlag =
            //                SectionOuterClass.SectionFlag.values()[sectionFlag.ordinal()];
            //            protoSection.addSectionFlags(protoSectionFlag);
            // could this not be:
            protoSection.addSectionFlagsValue(sectionFlag.ordinal());
        }
        return protoSection;
    }
}
