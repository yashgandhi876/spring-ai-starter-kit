// A small, safe Markdown renderer for model answers.
//
// Safety: nothing is ever parsed as HTML. parseMarkdown() turns text into a tree of a few known
// node types, and toDom() builds it with createElement/createTextNode, so "<script>" in an answer
// is shown as text. Links are kept only for http(s) URLs; anything else (javascript:, data:) stays text.
//
// Supported: paragraphs, line breaks, # headings, - and 1. lists, > quotes, --- rules, ``` code blocks,
// `inline code`, **bold**, *italic*, and [links](https://...). An unclosed ``` (while streaming)
// renders as a code block so far.
(function (root) {
  'use strict';

  const FENCE = /^\s*```\s*([\w+#.-]*)\s*$/;
  const HEADING = /^(#{1,6})\s+(.*?)\s*#*\s*$/;
  const RULE = /^\s*([-*_])(\s*\1){2,}\s*$/;
  const BULLET = /^\s*[-*+]\s+(.*)$/;
  const NUMBERED = /^\s*(\d{1,9})[.)]\s+(.*)$/;
  const QUOTE = /^\s*>\s?(.*)$/;
  const SAFE_URL = /^https?:\/\/[^\s]+$/i;

  // Groups: 1-2 code, 3 **bold**, 4 __bold__, 5 *italic*, 6 _italic_, 7-8 [text](url)
  const INLINE = new RegExp([
    '(`+)([\\s\\S]*?[^`])\\1(?!`)',
    '\\*\\*(?=\\S)([\\s\\S]*?\\S)\\*\\*',
    '__(?=\\S)([\\s\\S]*?\\S)__',
    '\\*([^\\s*](?:[^*]*?[^\\s*])?)\\*',
    '(?<![\\w])_([^\\s_](?:[^_]*?[^\\s_])?)_(?![\\w])',
    '\\[([^\\]\\n]+)\\]\\(([^)\\s]+)\\)'
  ].join('|'));

  function startsBlock(line) {
    return FENCE.test(line) || HEADING.test(line) || RULE.test(line)
        || BULLET.test(line) || NUMBERED.test(line) || QUOTE.test(line);
  }

  function parseInline(text) {
    const nodes = [];
    let rest = text;
    while (rest) {
      const m = INLINE.exec(rest);
      if (!m) {
        nodes.push({ type: 'text', text: rest });
        break;
      }
      if (m.index > 0) nodes.push({ type: 'text', text: rest.slice(0, m.index) });
      if (m[1] !== undefined) {
        nodes.push({ type: 'code', text: m[2].replace(/^ (.*) $/s, '$1') });
      } else if (m[3] !== undefined || m[4] !== undefined) {
        nodes.push({ type: 'strong', children: parseInline(m[3] ?? m[4]) });
      } else if (m[7] !== undefined) {
        nodes.push(SAFE_URL.test(m[8])
            ? { type: 'link', href: m[8], children: parseInline(m[7]) }
            : { type: 'text', text: m[0] });
      } else {
        nodes.push({ type: 'em', children: parseInline(m[5] ?? m[6]) });
      }
      rest = rest.slice(m.index + m[0].length);
    }
    return nodes;
  }

  function parseMarkdown(markdown) {
    const lines = String(markdown ?? '').replace(/\r\n?/g, '\n').split('\n');
    const blocks = [];
    let i = 0;
    while (i < lines.length) {
      const line = lines[i];
      let m;
      if (!line.trim()) {
        i++;
      } else if ((m = FENCE.exec(line))) {
        const code = [];
        i++;
        while (i < lines.length && !/^\s*```\s*$/.test(lines[i])) code.push(lines[i++]);
        i++; // closing fence, if any
        blocks.push({ type: 'pre', lang: m[1], text: code.join('\n') });
      } else if ((m = HEADING.exec(line))) {
        blocks.push({ type: 'heading', level: Math.min(m[1].length, 3), children: parseInline(m[2]) });
        i++;
      } else if (RULE.test(line)) {
        blocks.push({ type: 'hr' });
        i++;
      } else if (BULLET.test(line) || NUMBERED.test(line)) {
        const ordered = !BULLET.test(line);
        const item = ordered ? NUMBERED : BULLET;
        const list = { type: ordered ? 'ol' : 'ul', items: [] };
        if (ordered) list.start = Number(NUMBERED.exec(line)[1]);
        let current = null;
        while (i < lines.length && lines[i].trim()) {
          const im = item.exec(lines[i]);
          if (im) {
            current = [im[ordered ? 2 : 1]];
            list.items.push(current);
          } else if (startsBlock(lines[i])) {
            break;
          } else {
            current.push(lines[i].trim()); // continuation of the previous item
          }
          i++;
        }
        list.items = list.items.map(parts => parseInline(parts.join('\n')));
        blocks.push(list);
      } else if (QUOTE.test(line)) {
        const quoted = [];
        while (i < lines.length && QUOTE.test(lines[i])) quoted.push(QUOTE.exec(lines[i++])[1]);
        blocks.push({ type: 'blockquote', children: parseMarkdown(quoted.join('\n')) });
      } else {
        const para = [line.trim()];
        i++;
        while (i < lines.length && lines[i].trim() && !startsBlock(lines[i])) para.push(lines[i++].trim());
        blocks.push({ type: 'p', children: parseInline(para.join('\n')) });
      }
    }
    return blocks;
  }

  function appendText(doc, parent, text) {
    text.split('\n').forEach((part, index) => {
      if (index > 0) parent.appendChild(doc.createElement('br'));
      if (part) parent.appendChild(doc.createTextNode(part));
    });
  }

  function appendInline(doc, parent, nodes) {
    for (const node of nodes) {
      if (node.type === 'text') {
        appendText(doc, parent, node.text);
      } else if (node.type === 'code') {
        const el = doc.createElement('code');
        el.textContent = node.text;
        parent.appendChild(el);
      } else if (node.type === 'link') {
        const a = doc.createElement('a');
        a.href = node.href;
        a.rel = 'noopener noreferrer nofollow';
        a.target = '_blank';
        appendInline(doc, a, node.children);
        parent.appendChild(a);
      } else if (node.type === 'strong' || node.type === 'em') {
        const el = doc.createElement(node.type);
        appendInline(doc, el, node.children);
        parent.appendChild(el);
      }
    }
  }

  function toDom(blocks, doc) {
    const fragment = doc.createDocumentFragment();
    for (const block of blocks) {
      let el;
      if (block.type === 'pre') {
        el = doc.createElement('pre');
        const code = doc.createElement('code');
        code.textContent = block.text;
        el.appendChild(code);
      } else if (block.type === 'heading') {
        el = doc.createElement('h' + (block.level + 2)); // h3 to h5: answers sit below the page title
        appendInline(doc, el, block.children);
      } else if (block.type === 'hr') {
        el = doc.createElement('hr');
      } else if (block.type === 'ul' || block.type === 'ol') {
        el = doc.createElement(block.type);
        if (block.type === 'ol' && block.start !== 1) el.start = block.start;
        for (const item of block.items) {
          const li = doc.createElement('li');
          appendInline(doc, li, item);
          el.appendChild(li);
        }
      } else if (block.type === 'blockquote') {
        el = doc.createElement('blockquote');
        el.appendChild(toDom(block.children, doc));
      } else {
        el = doc.createElement('p');
        appendInline(doc, el, block.children);
      }
      fragment.appendChild(el);
    }
    return fragment;
  }

  /** Replaces the contents of element with the rendered markdown. */
  function renderMarkdown(element, markdown) {
    const doc = element.ownerDocument;
    element.replaceChildren(toDom(parseMarkdown(markdown), doc));
  }

  const api = { parseMarkdown, parseInline, toDom, renderMarkdown };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.Markdown = api;
})(typeof window !== 'undefined' ? window : globalThis);
