// Tests for the chat page's Markdown renderer. Run with: node --test src/test/js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { parseMarkdown, toDom } = require('../../main/resources/static/markdown.js');

// A minimal DOM: just enough of the API toDom uses. It has no innerHTML, so any attempt to
// parse HTML would fail, and serialize() escapes text the way a browser would show it.
class Node {
  constructor(tag) { this.tag = tag; this.children = []; this.attrs = {}; }
  appendChild(child) {
    if (child.tag === '#fragment') this.children.push(...child.children); else this.children.push(child);
    return child;
  }
  set textContent(text) { this.children = [new Text(String(text))]; }
  set href(v) { this.attrs.href = v; }
  set rel(v) { this.attrs.rel = v; }
  set target(v) { this.attrs.target = v; }
  set start(v) { this.attrs.start = String(v); }
}
class Text { constructor(text) { this.tag = '#text'; this.text = text; } }
const doc = {
  createElement: tag => new Node(tag),
  createTextNode: text => new Text(text),
  createDocumentFragment: () => new Node('#fragment'),
};
const escape = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
function serialize(node) {
  if (node.tag === '#text') return escape(node.text);
  const inner = node.children.map(serialize).join('');
  if (node.tag === '#fragment') return inner;
  const attrs = Object.entries(node.attrs).map(([k, v]) => ` ${k}="${escape(v)}"`).join('');
  return ['br', 'hr'].includes(node.tag) ? `<${node.tag}${attrs}>` : `<${node.tag}${attrs}>${inner}</${node.tag}>`;
}
const html = md => serialize(toDom(parseMarkdown(md), doc));

test('renders paragraphs, bold, italic and inline code', () => {
  assert.equal(html('Use **records** and *final* fields.\n\nCall `List.of()`.'),
      '<p>Use <strong>records</strong> and <em>final</em> fields.</p><p>Call <code>List.of()</code>.</p>');
});

test('keeps single line breaks inside a paragraph', () => {
  assert.equal(html('line one\nline two'), '<p>line one<br>line two</p>');
});

test('renders numbered and bulleted lists, as in a real llama3.2 answer', () => {
  const answer = 'Here are two tips:\n\n1. **Follow SRP**: one reason to change.\n2. **Use meaningful names**.\n\n- a\n- b';
  assert.equal(html(answer),
      '<p>Here are two tips:</p>'
      + '<ol><li><strong>Follow SRP</strong>: one reason to change.</li><li><strong>Use meaningful names</strong>.</li></ol>'
      + '<ul><li>a</li><li>b</li></ul>');
});

test('keeps the start number of an ordered list and joins continuation lines', () => {
  assert.equal(html('3. third\n   still third\n4. fourth'),
      '<ol start="3"><li>third<br>still third</li><li>fourth</li></ol>');
});

test('renders fenced code blocks without interpreting their contents', () => {
  assert.equal(html('```java\nvar x = a * b * c; // **not bold**\n```\nafter'),
      '<pre><code>var x = a * b * c; // **not bold**</code></pre><p>after</p>');
});

test('renders an unclosed fence while a reply is still streaming', () => {
  assert.equal(html('```\npartial'), '<pre><code>partial</code></pre>');
});

test('renders headings as h3 to h5, quotes and rules', () => {
  assert.equal(html('# Title\n## Sub\n#### Deep\n> quoted *text*\n\n---'),
      '<h3>Title</h3><h4>Sub</h4><h5>Deep</h5><blockquote><p>quoted <em>text</em></p></blockquote><hr>');
});

test('leaves snake_case words and lone asterisks alone', () => {
  assert.equal(html('my_var_name and 2 * 3 * 4'), '<p>my_var_name and 2 * 3 * 4</p>');
});

test('shows HTML in an answer as text instead of running it', () => {
  assert.equal(html('<img src=x onerror="alert(1)"> <script>alert(2)</script> **<b>hi</b>**'),
      '<p>&lt;img src=x onerror=&quot;alert(1)&quot;&gt; &lt;script&gt;alert(2)&lt;/script&gt; '
      + '<strong>&lt;b&gt;hi&lt;/b&gt;</strong></p>');
});

test('links only http and https URLs, opened safely in a new tab', () => {
  assert.equal(html('[Spring](https://spring.io/projects)'),
      '<p><a href="https://spring.io/projects" rel="noopener noreferrer nofollow" target="_blank">Spring</a></p>');
  for (const url of ['javascript:alert(1)', 'JaVaScRiPt:alert(1)', 'data:text/html,hi', '//evil.example', '/relative']) {
    assert.equal(html(`[click](${url})`), `<p>[click](${escape(url)})</p>`, url);
  }
});

test('handles empty and missing input', () => {
  assert.equal(html(''), '');
  assert.equal(html(undefined), '');
  assert.equal(html('\n\n  \n'), '');
});
