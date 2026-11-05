import { marked } from 'marked';
import * as api from '../api/models';

/** Generated from the API; the names here are the ones the app uses. */
export type Brief = api.BriefResponse;
export type BriefSection = api.BriefSectionResponse;
export type SectionKind = BriefSection['kind'];

/** One key per section: a round's update is keyed by its interview too. */
export function sectionKey(section: { kind: SectionKind; interviewId: string | null }): string {
  return section.interviewId ? `${section.kind}/${section.interviewId}` : section.kind;
}

/**
 * The section's Markdown as HTML. Checkboxes become ☐ and ☑ first, since inputs don't
 * survive Angular's sanitizing of [innerHTML], which every rendered section goes through.
 * Links to other sites (the company research's sources) open in a new tab, so the brief stays put.
 */
export function renderMarkdown(markdown: string): string {
  const boxes = markdown
    .replace(/^(\s*[-*+]) \[ \]/gm, '$1 ☐')
    .replace(/^(\s*[-*+]) \[[xX]\]/gm, '$1 ☑');
  const html = marked.parse(boxes, { async: false, gfm: true }) as string;
  return html.replace(/<a href="(https?:)/g, '<a target="_blank" rel="noopener" href="$1');
}

/** The whole brief as one Markdown file, for downloading. */
export function briefMarkdown(
  heading: string,
  glance: string[],
  sections: { title: string; content: string | null }[],
): string {
  const parts = [`# ${heading}`, '## At a glance', glance.map((line) => `- ${line}`).join('\n')];
  for (const section of sections) {
    if (!section.content?.trim()) continue;
    parts.push(`## ${section.title}`, section.content.trim());
  }
  return parts.join('\n\n') + '\n';
}
