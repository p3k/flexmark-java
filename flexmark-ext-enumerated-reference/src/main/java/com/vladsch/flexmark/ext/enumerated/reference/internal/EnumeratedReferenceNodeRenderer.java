package com.vladsch.flexmark.ext.enumerated.reference.internal;

import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ext.enumerated.reference.*;
import com.vladsch.flexmark.html.CustomNodeRenderer;
import com.vladsch.flexmark.html.HtmlWriter;
import com.vladsch.flexmark.html.renderer.*;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.DataHolder;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class EnumeratedReferenceNodeRenderer implements PhasedNodeRenderer
        // , PhasedNodeRenderer
{
    private final EnumeratedReferenceOptions options;
    private EnumeratedReferences enumeratedOrdinals;
    private Runnable ordinalRunnable;
    private final HtmlIdGenerator headerIdGenerator; // used for enumerated text reference

    public EnumeratedReferenceNodeRenderer(DataHolder options) {
        this.options = new EnumeratedReferenceOptions(options);
        ordinalRunnable = null;
        headerIdGenerator = new HeaderIdGenerator.Factory().create();
    }

    @Override
    public Set<RenderingPhase> getRenderingPhases() {
        LinkedHashSet<RenderingPhase> phaseSet = new LinkedHashSet<>();
        phaseSet.add(RenderingPhase.HEAD_TOP);
        phaseSet.add(RenderingPhase.BODY_TOP);
        return phaseSet;
    }

    @Override
    public void renderDocument(final NodeRendererContext context, final HtmlWriter html, final Document document, final RenderingPhase phase) {
        if (phase == RenderingPhase.HEAD_TOP) {
            headerIdGenerator.generateIds(document);
        } else if (phase == RenderingPhase.BODY_TOP) {
            enumeratedOrdinals = EnumeratedReferenceExtension.ENUMERATED_REFERENCE_ORDINALS.getFrom(document);
        }
    }

    @Override
    public Set<NodeRenderingHandler<?>> getNodeRenderingHandlers() {
        Set<NodeRenderingHandler<?>> set = new HashSet<NodeRenderingHandler<?>>();
        // @formatter:off
        set.add(new NodeRenderingHandler<EnumeratedReferenceText>(EnumeratedReferenceText.class, new CustomNodeRenderer<EnumeratedReferenceText>() { @Override public void render(EnumeratedReferenceText node, NodeRendererContext context, HtmlWriter html) { EnumeratedReferenceNodeRenderer.this.render(node, context, html); } }));
        set.add(new NodeRenderingHandler<EnumeratedReferenceLink>(EnumeratedReferenceLink.class, new CustomNodeRenderer<EnumeratedReferenceLink>() { @Override public void render(EnumeratedReferenceLink node, NodeRendererContext context, HtmlWriter html) { EnumeratedReferenceNodeRenderer.this.render(node, context, html); } }));
        set.add(new NodeRenderingHandler<EnumeratedReferenceBlock>(EnumeratedReferenceBlock.class, new CustomNodeRenderer<EnumeratedReferenceBlock>() { @Override public void render(EnumeratedReferenceBlock node, NodeRendererContext context, HtmlWriter html) { EnumeratedReferenceNodeRenderer.this.render(node, context, html); } }));// ,// zzzoptionszzz(CUSTOM_NODE)
        // @formatter:on
        return set;
    }

    private void render(final EnumeratedReferenceLink node, final NodeRendererContext context, final HtmlWriter html) {
        final String text = node.getText().toString();

        if (text.isEmpty()) {
            // placeholder for ordinal
            if (ordinalRunnable != null) ordinalRunnable.run();
        } else {
            enumeratedOrdinals.renderReferenceOrdinals(text, new OrdinalRenderer(this, context, html) {
                @Override
                public void startRendering(final EnumeratedReferenceRendering[] renderings) {
                    String title = new EnumRefTextCollectingVisitor().collectAndGetText(node.getChars().getBaseSequence(), renderings, null);
                    html.withAttr().attr("href", "#" + text).attr("title", title).tag("a");
                }

                @Override
                public void endRendering() {
                    html.tag("/a");
                }
            });
        }
    }

    private void render(EnumeratedReferenceText node, final NodeRendererContext context, final HtmlWriter html) {
        String text = node.getText().toString();

        if (text.isEmpty()) {
            // placeholder for ordinal
            if (ordinalRunnable != null) ordinalRunnable.run();
        } else {
            String type = EnumeratedReferenceRepository.getType(text.toString());

            if (type.isEmpty() || text.equals(type + ":")) {
                Node parent = node.getAncestorOfType(Heading.class);

                if (parent instanceof Heading) {
                    text = (type.isEmpty() ? text : type) + ":" + headerIdGenerator.getId(parent);
                }
            }

            enumeratedOrdinals.renderReferenceOrdinals(text, new OrdinalRenderer(this, context, html));
        }
    }

    private static class OrdinalRenderer implements EnumeratedOrdinalRenderer {
        final EnumeratedReferenceNodeRenderer renderer;
        final NodeRendererContext context;
        final HtmlWriter html;

        public OrdinalRenderer(final EnumeratedReferenceNodeRenderer renderer, final NodeRendererContext context, final HtmlWriter html) {
            this.renderer = renderer;
            this.context = context;
            this.html = html;
        }

        @Override
        public void startRendering(final EnumeratedReferenceRendering[] renderings) {

        }

        @Override
        public void setEnumOrdinalRunnable(final Runnable runnable) {
            renderer.ordinalRunnable = runnable;
        }

        @Override
        public Runnable getEnumOrdinalRunnable() {
            return renderer.ordinalRunnable;
        }

        @Override
        public void render(final int referenceOrdinal, final EnumeratedReferenceBlock referenceFormat, final String defaultText, final boolean needSeparator) {
            final Runnable compoundRunnable = renderer.ordinalRunnable;

            if (referenceFormat != null) {
                renderer.ordinalRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (compoundRunnable != null) compoundRunnable.run();
                        html.text(String.valueOf(referenceOrdinal));
                        if (needSeparator) html.text(".");
                    }
                };

                context.renderChildren(referenceFormat);
            } else {
                html.text(defaultText + " ");
                if (compoundRunnable != null) compoundRunnable.run();
                html.text(String.valueOf(referenceOrdinal));
                if (needSeparator) html.text(".");
            }
        }

        @Override
        public void endRendering() {

        }
    }

    private void render(EnumeratedReferenceBlock node, NodeRendererContext context, HtmlWriter html) {

    }

    public static class Factory implements NodeRendererFactory {
        @Override
        public NodeRenderer apply(final DataHolder options) {
            return new EnumeratedReferenceNodeRenderer(options);
        }
    }
}
