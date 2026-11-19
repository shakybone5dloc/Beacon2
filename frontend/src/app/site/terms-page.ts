import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SiteState } from './site-state';
import { POLICY_STYLES } from './policy-styles';

/** The terms page; open to everyone, signed in or not. */
@Component({
  selector: 'app-terms-page',
  imports: [RouterLink],
  templateUrl: './terms-page.html',
  styles: POLICY_STYLES,
})
export class TermsPage implements OnInit {
  protected readonly site = inject(SiteState);

  ngOnInit(): void {
    this.site.load();
  }
}
