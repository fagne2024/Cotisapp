import { Pipe, PipeTransform } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

@Pipe({
  name: 'highlight',
  standalone: true,
})
export class HighlightPipe implements PipeTransform {
  constructor(private sanitizer: DomSanitizer) {}

  transform(text: string, query: string): SafeHtml {
    if (!query || !text) {
      return text;
    }

    const queryLower = query.toLowerCase();
    const textLower = text.toLowerCase();

    // Find all occurrences of the query
    let result = text;
    let lastIndex = 0;
    let highlighted = '';

    let index = textLower.indexOf(queryLower);
    while (index !== -1) {
      // Add text before match
      highlighted += this.escapeHtml(text.substring(lastIndex, index));
      // Add highlighted match
      highlighted += `<mark style="background-color: #ffd700; font-weight: 600; padding: 0 2px; border-radius: 3px;">`;
      highlighted += this.escapeHtml(text.substring(index, index + queryLower.length));
      highlighted += `</mark>`;
      lastIndex = index + queryLower.length;
      index = textLower.indexOf(queryLower, lastIndex);
    }

    // Add remaining text
    highlighted += this.escapeHtml(text.substring(lastIndex));

    return this.sanitizer.bypassSecurityTrustHtml(highlighted);
  }

  private escapeHtml(text: string): string {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }
}
