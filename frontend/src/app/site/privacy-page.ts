import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SiteState } from './site-state';
import { POLICY_STYLES } from './policy-styles';

/** The privacy page; open to everyone, signed in or not. */
@Component({
  selector: 'app-privacy-page',
  imports: [RouterLink],
  templateUrl: './privacy-page.html',
  styles: POLICY_STYLES,
})
export class PrivacyPage implements OnInit {
  protected readonly site = inject(SiteState);

  ngOnInit(): void {
    this.site.load();
  }
}
