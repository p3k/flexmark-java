package com.vladsch.flexmark.internal;

import com.vladsch.flexmark.parser.ListOptions;
import com.vladsch.flexmark.ast.*;
import com.vladsch.flexmark.ast.util.Parsing;
import com.vladsch.flexmark.parser.ParserEmulationFamily;
import com.vladsch.flexmark.parser.block.*;

import static com.vladsch.flexmark.parser.ParserEmulationFamily.*;

public class ListItemParser extends AbstractBlockParser {
    private final ListItem myBlock;
    private final ListOptions myOptions;
    private final ListBlockParser.ListData myListData;
    private final Parsing myParsing;
    private boolean myHadBlankLine = false;
    private boolean myIsEmpty = false;

    ListItemParser(ListOptions options, Parsing parsing, ListBlockParser.ListData listData) {
        myOptions = options;
        myListData = listData;
        myParsing = parsing;
        myBlock = myListData.isNumberedList ? new OrderedListItem() : new BulletListItem();
        myBlock.setOpeningMarker(myListData.listMarker);
    }

    @SuppressWarnings({ "WeakerAccess", "unused" })
    int getContentColumn() {
        return myListData.markerColumn + myListData.contentOffset;
    }

    @SuppressWarnings("WeakerAccess")
    int getContentIndent() {
        return myListData.markerIndent + myListData.listMarker.length() + myListData.contentOffset;
    }

    @Override
    public boolean isContainer() {
        return true;
    }

    @Override
    public boolean canContain(Block block) {
        return true;
    }

    @Override
    public boolean isPropagatingLastBlankLine(BlockParser lastMatchedBlockParser) {
        return !(myBlock.getFirstChild() == null && this != lastMatchedBlockParser);
    }

    @Override
    public Block getBlock() {
        return myBlock;
    }

    @Override
    public void closeBlock(ParserState state) {
        myBlock.setCharsFromContent();
    }

    private BlockContinue continueAtColumn(int newColumn) {
        // reset our empty flag, we have content now so we stay open
        myIsEmpty = false;
        return BlockContinue.atColumn(newColumn);
    }

    @Override
    public BlockContinue tryContinue(ParserState state) {
        if (state.isBlank()) {
            // now when we have a Blank line after empty list item, now we need to handle it because the list is not closed and we handle next list item conditions
            myIsEmpty = myBlock.getFirstChild() == null;
            myHadBlankLine = true;
            return BlockContinue.atIndex(state.getNextNonSpaceIndex());
        }

        assert myBlock.getParent() instanceof ListBlock;

        ListBlockParser listBlockParser = (ListBlockParser) state.getActiveBlockParser(myBlock.getParent());

        ParserEmulationFamily emulationFamily = myOptions.parserEmulationFamily;
        int newColumn = state.getColumn() + getContentIndent();
        if (emulationFamily == COMMONMARK) {
            // - CommonMark: version 0.27 of the spec, all common mark parsers
            //     - Definitions/Defaults:
            //         - `ITEM_INDENT` = 4 <!-- not used -->
            //         - `CODE_INDENT` = 4
            //         - `current indent` = `line indent`
            //     - Start List Conditions:
            //         - `item indent` < `CODE_INDENT`: new list with new item
            //         - `item content indent` >= `CODE_INDENT`: empty item, indented code
            //     - Continuation Conditions:
            //         - `current indent` >= `list last content indent` + `CODE_INDENT`: indented code
            //         - `current indent` >= `list last content indent`: sub-item
            //         - `current indent` >= `list indent`: list item

            int currentIndent = state.getIndent();

            if (currentIndent >= getContentIndent() + myOptions.codeIndent) {
                // our indented code child
                listBlockParser.setItemHandledLine(state.getLine());
                return continueAtColumn(newColumn);
            } else {
                ListBlockParser.ListData listData = ListBlockParser.parseListMarker(myOptions.codeIndent, state);

                if (currentIndent >= getContentIndent()) {
                    if (listData != null) {
                        BlockParser matched = state.getActiveBlockParser();
                        boolean inParagraph = matched.isParagraphParser();
                        boolean inParagraphListItem = inParagraph && matched.getBlock().getParent() instanceof ListItem && matched.getBlock() == matched.getBlock().getParent().getFirstChild();

                        if (inParagraphListItem
                                && (!myOptions.canInterrupt(listData.listBlock, listData.isEmpty, true)
                                || !myOptions.canStartSubList(listData.listBlock, listData.isEmpty))) {
                            // just a lazy continuation of us
                            listBlockParser.setItemHandledLineSkipActive(state.getLine());
                            return continueAtColumn(newColumn);
                        } else {
                            // our sub list item
                            listBlockParser.setItemHandledNewListLine(state.getLine());
                            return continueAtColumn(newColumn);
                        }
                    } else {
                        if (myIsEmpty) {
                            // our child item, other than a list item, if we are empty then no such thing
                            listBlockParser.setItemHandledLine(state.getLine());
                            return BlockContinue.none();
                        } else {
                            listBlockParser.setItemHandledLine(state.getLine());
                            return continueAtColumn(newColumn);
                        }
                    }
                } else if (listData != null) {
                    if (!myHadBlankLine && !myOptions.canInterrupt(listData.listBlock, listData.isEmpty, true)) {
                        // our text or lazy continuation
                        listBlockParser.setItemHandledLine(state.getLine());
                        return continueAtColumn(state.getColumn() + currentIndent);
                    } else {
                        // here have to see if the item is really a mismatch and we sub-list mismatches
                        if (myOptions.startSubList(listBlockParser.getBlock(), listData.listBlock)) {
                            // we keep it as our sub-item
                            listBlockParser.setItemHandledNewListLine(state.getLine());
                            return continueAtColumn(state.getColumn() + currentIndent);
                        } else {
                            if (myOptions.startNewList(listBlockParser.getBlock(), listData.listBlock)) {
                                // a new list
                                listBlockParser.setItemHandledNewListLine(state.getLine());
                                return BlockContinue.none();
                            } else {
                                // the next line in the list
                                listBlockParser.setItemHandledNewItemLine(state.getLine());
                                return BlockContinue.none();
                            }
                        }
                    }
                }
            }
        } else if (emulationFamily == MULTI_MARKDOWN) {
            // - MultiMarkdown: Pandoc, MultiMarkdown, Pegdown
            //     - Definitions/Defaults:
            //         - `ITEM_INDENT` = 4
            //         - `CODE_INDENT` = 8
            //         - `current indent` = `line column` - `first parent list column` + `first parent list
            //           indent` - (`list nesting` - 1) * `ITEM_INDENT`
            //     - Start List Conditions:
            //         - `current indent` < `ITEM_INDENT`: new list with new item
            //     - Continuation Conditions:
            //          - `current indent` >= `CODE_INDENT`: indented code
            //          - `current indent` >= `ITEM_INDENT`: sub-item
            //          - `current indent` < `ITEM_INDENT`: list item

            ListBlockParser firstParent = null;
            int listLevel = 0;

            for (BlockParser blockParser : state.getActiveBlockParsers()) {
                if (blockParser instanceof ListBlockParser) {
                    firstParent = (ListBlockParser) blockParser;
                    listLevel++;
                } else if (!(blockParser instanceof ListItemParser)) {
                    break;
                }
            }

            int firstParentListColumn = firstParent == null ? state.getColumn() : firstParent.getListData().markerColumn;
            int firstParentListIndent = firstParent == null ? state.getIndent() : firstParent.getListData().markerIndent;
            if (listLevel == 0) listLevel = 1;

            int currentIndent = state.getColumn() - firstParentListColumn + firstParentListIndent - myOptions.itemIndent * (listLevel - 1);

            if (currentIndent >= myOptions.codeIndent) {
                // our indented code child
                listBlockParser.setItemHandledLine(state.getLine());
                return continueAtColumn(newColumn);
            } else {
                ListBlockParser.ListData listData = ListBlockParser.parseListMarker(-1, state);

                if (currentIndent >= myOptions.itemIndent) {
                    if (listData != null) {
                        // our sub list item
                        listBlockParser.setItemHandledNewListLine(state.getLine());
                        return continueAtColumn(newColumn);
                    } else {
                        // our child item, other than a list item
                        listBlockParser.setItemHandledLine(state.getLine());
                        return continueAtColumn(newColumn);
                    }
                } else {
                    if (!myHadBlankLine && (listData == null || !myOptions.canInterrupt(listData.listBlock, listData.isEmpty, true))) {
                        // our text or lazy continuation
                        listBlockParser.setItemHandledLine(state.getLine());
                        return continueAtColumn(state.getColumn() + currentIndent);
                    } else if (listData != null && currentIndent >= myListData.markerIndent) {
                        // here have to see if the item is really a mismatch and we sub-list mismatches
                        if (myOptions.startSubList(listBlockParser.getBlock(), listData.listBlock)) {
                            // we keep it as our sub-item
                            listBlockParser.setItemHandledNewListLine(state.getLine());
                            return continueAtColumn(state.getColumn() + currentIndent);
                        } else {
                            // the next line in the list
                            listBlockParser.setItemHandledNewItemLine(state.getLine());
                            return BlockContinue.none();
                        }
                    }
                }
            }
        } else if (emulationFamily == KRAMDOWN) {
            // - Kramdown:
            //     - Definitions/Defaults:
            //         - `ITEM_INDENT` = 4
            //         - `CODE_INDENT` = 8
            //         - `current indent` = `line indent`
            //     - Start List Conditions:
            //         - `current indent` < `ITEM_INDENT`: new list with new item
            //     - Continuation Conditions:
            //         - `current indent` >= `list content indent` + `CODE_INDENT`: indented code
            //         - `current indent` >= `list content indent` + `ITEM_INDENT`:
            //             - if had blank line in item && have previous list item parent && list item line: then let it have it
            //             - otherwise: lazy continuation of last list item
            //         - `current indent` >= `item content indent`: sub-item
            //         - `current indent` >= `list content indent`: list item

            int currentIndent = state.getIndent();
            int listContentIndent = listBlockParser.getContentIndent();

            if (currentIndent >= listContentIndent + myOptions.codeIndent) {
                // our indented code child
                listBlockParser.setItemHandledLine(state.getLine());
                return continueAtColumn(newColumn);
            } else {
                ListBlockParser.ListData listData = ListBlockParser.parseListMarker(-1, state);

                if (currentIndent >= listContentIndent + myOptions.itemIndent) {
                    // this could be indented code of the parent list item or our lazy continuation or our child
                    if (listData == null || !(myBlock.getParent().getParent() instanceof ListItem)) {
                        if (!myHadBlankLine && (listData == null || !myOptions.canInterrupt(listData.listBlock, listData.isEmpty, true))) {
                            // our text or lazy continuation
                            listBlockParser.setItemHandledLine(state.getLine());
                            return continueAtColumn(state.getColumn() + currentIndent);
                        } else if (listData != null && currentIndent >= myListData.markerIndent) {
                            // here have to see if the item is really a mismatch and we sub-list mismatches
                            if (myOptions.startSubList(listBlockParser.getBlock(), listData.listBlock)) {
                                // we keep it as our sub-item
                                listBlockParser.setItemHandledNewListLine(state.getLine());
                                return continueAtColumn(state.getColumn() + currentIndent);
                            } else {
                                // the next line in the list
                                listBlockParser.setItemHandledNewItemLine(state.getLine());
                                return BlockContinue.none();
                            }
                        }
                    }
                } else if (currentIndent >= listContentIndent) {
                    // our sub item
                    if (listData != null) {
                        // our sub list item
                        listBlockParser.setItemHandledNewListLine(state.getLine());
                        return continueAtColumn(newColumn);
                    } else {
                        // our child item, other than a list item
                        listBlockParser.setItemHandledLine(state.getLine());
                        return continueAtColumn(newColumn);
                    }
                }
            }
        } else if (emulationFamily == MARKDOWN) {
            // - Markdown:
            //     - Definitions/Defaults:
            //         - `ITEM_INDENT` = 4
            //         - `CODE_INDENT` = 8
            //         - `current indent` = `line indent`
            //     - Start List Conditions:
            //         - `current indent` < `ITEM_INDENT`: new list with new item
            //     - Continuation Conditions:
            //         - `current indent` >= `list indent` + `CODE_INDENT`:
            //             - if had blank line in item: indented code child
            //             - otherwise: lazy continuation of last list item
            //         - `current indent` > `list indent`: sub-item or non-list child
            //         - `current indent` == `list indent` && list item line: new list item
            //         - otherwise: not our business

            int currentIndent = state.getIndent();
            int listIndent = listBlockParser.getListData().markerIndent;

            if (currentIndent >= listIndent + myOptions.codeIndent) {
                // this could be indented code or our lazy continuation
                if (!myHadBlankLine) {
                    // our lazy continuation
                    listBlockParser.setItemHandledLine(state.getLine());
                    return continueAtColumn(state.getColumn() + currentIndent);
                } else {
                    // indented code child
                    listBlockParser.setItemHandledLine(state.getLine());
                    return continueAtColumn(state.getColumn() + listIndent + myOptions.itemIndent);
                }
            } else {
                ListBlockParser.ListData listData = ListBlockParser.parseListMarker(-1, state);

                if (currentIndent > listIndent) {
                    // our sub item or non-list item child
                    if (listData != null) {
                        // our sub list item
                        listBlockParser.setItemHandledNewListLine(state.getLine());
                        return continueAtColumn(newColumn);
                    } else {
                        // our child item, other than a list item
                        listBlockParser.setItemHandledLine(state.getLine());
                        return continueAtColumn(newColumn);
                    }
                } else if (currentIndent == listIndent && listData != null) {
                    if (myHadBlankLine || myOptions.canInterrupt(listData.listBlock, listData.isEmpty, true)) {
                        // the next line in the list
                        listBlockParser.setItemHandledNewItemLine(state.getLine());
                        return BlockContinue.none();
                    }
                }
            }
        }
        return BlockContinue.none();
    }
}