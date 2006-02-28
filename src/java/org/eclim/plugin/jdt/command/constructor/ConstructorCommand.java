/**
 * Copyright (c) 2004 - 2005
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
package org.eclim.plugin.jdt.command.constructor;

import java.io.IOException;
import java.io.StringWriter;

import java.util.HashMap;

import org.apache.commons.lang.StringUtils;

import org.eclim.Services;

import org.eclim.command.AbstractCommand;
import org.eclim.command.CommandLine;
import org.eclim.command.Options;

import org.eclim.plugin.jdt.JavaUtils;
import org.eclim.plugin.jdt.TypeUtils;

import org.eclim.util.VelocityFormat;

import org.eclim.util.file.Position;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.Signature;

/**
 * Command used to generate class constructors.
 *
 * @author Eric Van Dewoestine (ervandew@yahoo.com)
 * @version $Revision$
 */
public class ConstructorCommand
  extends AbstractCommand
{
  private static final String TEMPLATE = "java/constructor.vm";

  /**
   * {@inheritDoc}
   */
  public Object execute (CommandLine _commandLine)
    throws IOException
  {
    try{
      String project = _commandLine.getValue(Options.PROJECT_OPTION);
      String file = _commandLine.getValue(Options.FILE_OPTION);
      String propertiesOption = _commandLine.getValue(Options.PROPERTIES_OPTION);
      String[] properties = {};
      if(propertiesOption != null){
        properties = StringUtils.split(propertiesOption, ',');
      }
      int offset = _commandLine.getIntValue(Options.OFFSET_OPTION);

      ICompilationUnit src = JavaUtils.getCompilationUnit(project, file);
      IType type = TypeUtils.getType(src, offset);
      for(int ii = 0; ii < properties.length; ii++){
        if(!type.getField(properties[ii]).exists()){
          throw new RuntimeException(
              Services.getMessage("field.not.found",
                new Object[]{properties[ii], type.getElementName()}));
        }
      }

      // check if constructor already exists.
      IMethod method = null;

      if(properties.length > 0){
        method = type.getMethod(type.getElementName(), null);
      }else{
        String[] fieldSigs = new String[properties.length];
        for (int ii = 0; ii < properties.length; ii++){
          fieldSigs[ii] = type.getField(properties[ii]).getTypeSignature();
        }
        method = type.getMethod(type.getElementName(), fieldSigs);
      }
      if(method.exists()){
        throw new RuntimeException(
            Services.getMessage("constructor.already.exists",
              type.getElementName() + " (" + buildParams(type, properties) + ")"));
      }

      IJavaElement sibling = null;
      IMethod[] methods = type.getMethods();
      for(int ii = 0; ii < methods.length; ii++){
        if(methods[ii].isConstructor()){
          sibling = ii < methods.length - 1 ? methods[ii + 1] : null;
        }
      }
      // insert before any inner classes if any.
      if(sibling == null){
        IType[] types = type.getTypes();
        sibling = types != null && types.length > 0 ? types[0] : null;
      }

      HashMap values = new HashMap();
      values.put("type", type.getElementName());
      if(properties != null && properties.length > 0){
        values.put("fields", properties);
        values.put("params", buildParams(type, properties));
      }

      StringWriter writer = new StringWriter();
      VelocityFormat.evaluate(
          values, VelocityFormat.getTemplate(TEMPLATE), writer);
      Position position = TypeUtils.getPosition(type,
          type.createMethod(writer.toString(), sibling, false, null));

      return filter(_commandLine, position);
    }catch(Exception e){
      return e;
    }
  }

  /**
   * Builds a string of the constructor parameters.
   *
   * @param _type The type containing the fields.
   * @param _fields The array of fields.
   * @return The parameters string.
   */
  protected String buildParams (IType _type, String[] _fields)
    throws Exception
  {
    StringBuffer params = new StringBuffer();
    for (int ii = 0; ii < _fields.length; ii++){
      if(ii != 0){
        params.append(", ");
      }
      IField field = _type.getField(_fields[ii]);
      params.append(Signature.getSignatureSimpleName(field.getTypeSignature()))
        .append(' ').append(field.getElementName());
    }
    return params.toString();
  }
}
