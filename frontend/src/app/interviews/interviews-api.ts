import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import {
  deleteInterview,
  getInterviewCalendar,
  listApplicationInterviews,
  scheduleInterview,
  updateInterview,
} from '../api/functions';
import { Interview, ScheduleInterviewRequest, UpdateInterviewRequest } from './interview.model';

@Injectable({ providedIn: 'root' })
export class InterviewsApi {
  private readonly api = inject(BeaconApi);

  /** Earliest first. */
  onApplication(applicationId: string): Observable<Interview[]> {
    return this.api.invoke(listApplicationInterviews, { applicationId });
  }

  /** Interviewers not on the application yet are added to it. */
  schedule(applicationId: string, request: ScheduleInterviewRequest): Observable<Interview> {
    return this.api.invoke(scheduleInterview, { applicationId, body: request });
  }

  update(id: string, request: UpdateInterviewRequest): Observable<Interview> {
    return this.api.invoke(updateInterview, { id, body: request });
  }

  delete(id: string): Observable<void> {
    return this.api.invoke(deleteInterview, { id });
  }

  /** A link the browser downloads as an .ics file. */
  calendarUrl(interview: Interview): string {
    return getInterviewCalendar.PATH.replace('{id}', interview.id);
  }
}
