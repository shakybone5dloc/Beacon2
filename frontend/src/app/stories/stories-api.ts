import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import { createStory, deleteStory, draftStories, listStories, updateStory } from '../api/functions';
import { SaveStoryRequest, Story, StoryDraft } from './story.model';

/** The story bank, and AI drafts of new stories to review before saving. */
@Injectable({ providedIn: 'root' })
export class StoriesApi {
  private readonly api = inject(BeaconApi);

  /** Most recently changed first. */
  list(): Observable<Story[]> {
    return this.api.invoke(listStories);
  }

  create(request: SaveStoryRequest): Observable<Story> {
    return this.api.invoke(createStory, { body: request });
  }

  update(id: string, request: SaveStoryRequest): Observable<Story> {
    return this.api.invoke(updateStory, { id, body: request });
  }

  delete(id: string): Observable<void> {
    return this.api.invoke(deleteStory, { id });
  }

  /** One story from the notes, or a few from the resume when there are none. Nothing is saved. */
  draft(notes: string | null, resumeDocumentId: string | null = null): Observable<StoryDraft[]> {
    return this.api.invoke(draftStories, {
      body: { notes: notes?.trim() || null, resumeDocumentId },
    });
  }
}
