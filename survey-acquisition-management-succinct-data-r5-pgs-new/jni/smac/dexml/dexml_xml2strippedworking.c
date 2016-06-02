#include <stdio.h>
#include <strings.h>
#include <unistd.h>
#include <stdlib.h>

/*
  Re-constitute a form to XML (or other format) by reading a template of the output
  format and substituting the values in.
*/
int stripped2xml(char *stripped,int stripped_len,char *template,int template_len,char *xml,int xml_size)
{
  int xml_ofs=0;
  int state=0;

  int subform_depth=0;

  int current_subform_record_id=0;
  int state_in_subform_record=0;

  int current_subform_instance_id=0;
  int state_in_subform_instance=0;

  int record_count[1024];
  int record_number=0;

  int m;

  for(m=0;m<1024;m++){
	  record_count[m]=0;
  }

  int i,j,k;

  char *fieldnames[1024];
  char *values[1024];
  int  in_subform_record[1024];
  int  record_number_field[1024];

  int field_count=0;
  
  char field[1024];
  int field_len=0;

  char value[1024];
  int value_len=0;
  
  char other[1024];
  int other_len=0;

  // Read fields from stripped.

  /*
   * Original algorithm :
   *
   * LOOP (Not end of file)
   * 	READ CHARACTER
   *	IF STATE==0 : FIELD+=CHARACTER; ENDIF
   *	IF CHARACTER== '=' STATE=1
   *	IF STATE==1 : VALUE+=CHARACTER; ENDIF
   */
  for(i=0;i<stripped_len;i++) {

    if (stripped[i]=='='&&(state==0)) {
      state=1;
    }
    //For the moment, managing only 1 depth of subforms (no subforms within subforms allowed)
    else if(stripped[i]=='{') {

    	printf("Found opening bracket ! \n");

    	if (state_in_subform_instance ==0) {
    		state_in_subform_instance=1;
    	}
    	else {
    		record_count[record_number]++;
    		state_in_subform_record=1;
    	}
    }
    else if(stripped[i]=='}') {

    printf("Found closing bracket ! \n");

		if (state_in_subform_record==1) {
			state_in_subform_record=0;
		}
		else {
			state_in_subform_instance=0;
			record_number++;
		}
    }
    else if (stripped[i]<' ') {
      if (state==1) {
	// record field=value pair
	field[field_len]=0;
	value[value_len]=0;
	fieldnames[field_count]=strdup(field);
	values[field_count]=strdup(value);
	in_subform_record[field_count]=state_in_subform_record;
	record_number_field[field_count]=record_number;
	printf("---\n@@@ fieldname = %s \n",fieldnames[field_count]);
	printf("@@@ value = %s \n",values[field_count]);
	printf("@@@ part of subform record ? = %d \n",in_subform_record[field_count]);
	printf("@@@ subform record temporary number = %d \n",record_number);
	printf("@@@ Record count = %d \n --- \n",record_count[record_number]);
	field_count++;
      }
      state=0;
      field_len=0;
      value_len=0;
    } else {
      if (field_len>1000||value_len>1000) return -1;
      if (state==0) field[field_len++]=stripped[i];
      else value[value_len++]=stripped[i];
    }
  }

  // Read template, substituting $FIELD$ with the value of the field.
  // $$ substitutes to a single $ character.
  state=0; field_len=0;
  for(i=0;i<template_len;i++) {
//	if (template[i]=='<') {
//	}
//	if (template[i]=='>') {
//		other[other_len]=0; field_len=0;
//		printf("hhhhhhhh \n");
//	}
    if (template[i]=='$') {
      if (state==1) {
	// end of variable
	field[field_len]=0; field_len=0;
	for(j=0;j<field_count;j++)
	{
//	  //printf("====> fieldnames %d = %s \n",j,fieldnames[j]);
//	  //printf("====> field %d = %s \n",j,field);
//	  printf("====> other %d = %s \n",j,other);
	  if (!strcasecmp(field,fieldnames[j])) {
	    // write out field value
//		if(in_subform_record[j]&&record_count[record_number_field[j]]>0){
//			//i=index_beginning_record;
//			 printf("!!!!!!!!!!!! Let's restart a record ! \n");
//		}
	    for(k=0;values[j][k];k++) {
	      xml[xml_ofs++]=values[j][k];
	      if (xml_ofs==xml_size+100000) return -1;
	    }
	      printf("### values = %s \n",values[j]);
	    break;
	  }
	}

	state=0;
      } else {
	// start of variable substitution
	state=1;
      }
    } else {
      if (state==1) {
	// accumulate field name
	if (field_len<1023) {
	  field[field_len++]=template[i];
	  field[field_len]=0;
	}
      } else {
	// natural character
	xml[xml_ofs++]=template[i];
//	if(other_len<1023) {
//		other[other_len++]=template[i];
//		other[other_len]=0;
//	}
	if (xml_ofs==xml_size+100000) return -1;
      }
    }
  }
  return xml_ofs;
}

int xml2stripped(const char *form_name, const char *xml,int xml_len,
		 char *stripped,int stripped_size)
{

  /* rbk - start */

  int in_subform=0;

  /* rbk - end */

  char tag[1024];
  int taglen=0;

  char value[1024];
  int val_len=0;

  int in_instance=0;
  
  int interesting_tag=0;

  int state=0;

  int xmlofs=0;
  int stripped_ofs=0;

  char exit_tag[1024]="";
  

  int c=xml[xmlofs++];
  while(c>=-1&&(xmlofs<xml_len)) {
    switch(c) {
    case '\n': case '\r': break;
    case '<': 
      state=1;
      if (interesting_tag&&val_len>0) {
	value[val_len]=0;
	// Magpi puts ~ in empty fields -- don't include these in the stripped output
	if ((value[0]=='~')&&(val_len==1)) {
	  // nothing to do
	}

	/* Added code : April 2016 (handling subforms) */
	else if (!strncasecmp("dd:subform",tag,strlen("dd:subform"))) {
	  // nothing to do
	}
	/* End added code */

	else {
	  int b=snprintf(&stripped[stripped_ofs],stripped_size-stripped_ofs,"%s=%s\n",tag,value);
	  if (b>0) stripped_ofs+=b;
	}
	val_len=0;
      }
      interesting_tag=0; 
      break;
    case '>': 
      if (taglen) {
	// got a tag name	
	tag[taglen]=0;
	interesting_tag=0;
	if (tag[0]!='/'&&in_instance&&tag[taglen-1]!='/') {
	  interesting_tag=1;
	}
	if (!form_name) {
	  /*
	    Magpi forms don't include the form name in the xml.
	    We have to get the form name from the formid field.

	    ODK Collect on the other hand provides the form name as an
	    id attribute of a tag which follows an <instance> tag.
	  */
	  if (!strncasecmp("form",tag,strlen("form")))
	    {
	      //	    if (!in_instance) printf("Found start of instance\n");
	      in_instance++;
	    }
	  if ((!strncasecmp("form",&tag[1],strlen("form")))
	      &&tag[0]=='/')
	    {
	      in_instance--;
	      //	    if (!in_instance) printf("Found end of instance\n");
	    }


/* Added code : April 2016 (handling subforms) */
	  if(!strncasecmp("dd:subform",tag,strlen("dd:subform")))
	  {
		  //we are in the start of a subform
		  in_subform++;
		  printf("Test-Dialog / dexml.c : Found start of subform ! Tag = %s \n",tag);
			int b=snprintf(&stripped[stripped_ofs],stripped_size-stripped_ofs,"{\n");
			if (b>0) stripped_ofs+=b;
	  }

	  if ((!strncasecmp("dd:subform",&tag[1],strlen("dd:subform")))
	  	      &&tag[0]=='/')
	  {
		  //we reached the end of the subform
	  	  in_subform--;
	  	  printf("Test-Dialog / dexml.c : Reached end of subform ! Tag = &s \n",tag);
			int b=snprintf(&stripped[stripped_ofs],stripped_size-stripped_ofs,"}\n");
			if (b>0) stripped_ofs+=b;
	  }

	  if((!strncasecmp("data",tag,strlen("data")))&&in_subform)
	  {
		  int b=snprintf(&stripped[stripped_ofs],stripped_size-stripped_ofs,"{\n");
		  if (b>0) stripped_ofs+=b;
	  }

	  if ((!strncasecmp("data",&tag[1],strlen("data")))&&(tag[0]=='/')&&(in_subform))
	  {
		  int b=snprintf(&stripped[stripped_ofs],stripped_size-stripped_ofs,"}\n");
		  if (b>0) stripped_ofs+=b;
	  }

/* end added code */

	  if (!in_instance) {
	    // ODK form name appears as attributes of a tag which has a named based
	    // on the name of the form.
	    char name_part[1024];
	    char version_part[1024];
	    int r=0;
	    if (strlen(tag)<1024) {
	      r=sscanf(tag,"%s id=\"%[^\"]\" version=\"%[^\"]\"",
		       exit_tag,name_part,version_part);
	    }
	    if (r==3) {
	      // Add implied formid tag for ODK forms so that we can more easily find
	      // the recipe that corresponds to a record.
	      fprintf(stderr,"ODK form name is %s.%s\n",
		      name_part,version_part);
	      int b=snprintf(&stripped[stripped_ofs],stripped_size-stripped_ofs,"formid=%s.%s\n",name_part,version_part);
	      if (b>0) stripped_ofs+=b;
	      in_instance++;
	    }

	  }
	  if (in_instance&&exit_tag[0]&&tag[0]=='/'&&!strcasecmp(&tag[1],exit_tag))
	    {
	      // Found matching tag for the ODK instance opening tag, so end
	      // form instance
	      in_instance--;
	    }
	} else {
	  if (!strncasecmp(form_name,tag,strlen(form_name)))
	    {
	      in_instance++;
	    }
	  if ((!strncasecmp(form_name,&tag[1],strlen(form_name)))
	      &&tag[0]=='/')
	    {
	      in_instance--;
	    }
	}
	taglen=0;
      }
      state=0; break; // out of a tag
    default:
      if (state==1) {
	// in a tag specification, so accumulate name of tag
	if (taglen<1000) tag[taglen++]=c;
      }
      if (interesting_tag) {
	// exclude leading spaces from values
	if (val_len||(c!=' ')) {
	  if (val_len<1000) value[val_len++]=c;
	}
      }
    }
    c= xml[xmlofs++];
  }
  return stripped_ofs;  
}
