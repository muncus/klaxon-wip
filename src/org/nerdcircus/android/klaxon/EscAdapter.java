/* 
 * Copyright 2008 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nerdcircus.android.klaxon;

import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

public class EscAdapter extends ResourceCursorAdapter
{
    public EscAdapter(Context context, int layout, Cursor c){
        super(context, layout, c);
    }

    /**
     * Display a textview with the page's content.  Use a compound drawable to
     * display an indicator to the left of the textview as to whether or not the
     * page has been responded to.
     */
    public void bindView(View view, Context context, Cursor cursor){
        TextView subject = (TextView) view.findViewById(R.id.subject);

        if(cursor.getString(cursor.getColumnIndex(Pager.Pages.SUBJECT)) != null){
          subject.setText(cursor.getString(cursor.getColumnIndex(Pager.Pages.SUBJECT)));
        }

        int status = cursor.getInt(cursor.getColumnIndex(Pager.Pages.ACK_STATUS));

        Drawable icon = context.getResources().getDrawable(Pager.getStatusResId(status));
        subject.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);

        TextView subtext = (TextView) view.findViewById(R.id.subtext);
        if(subtext != null && cursor.getString(cursor.getColumnIndex(Pager.Pages.FROM_ADDR)) != null){
            subtext.setText(cursor.getString(cursor.getColumnIndex(Pager.Pages.FROM_ADDR)));
        }


    }

    

}

