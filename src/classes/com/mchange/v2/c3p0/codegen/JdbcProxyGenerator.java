/*
 * Distributed as part of c3p0 v.0.8.5pre4
 *
 * Copyright (C) 2003 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 2.1, as 
 * published by the Free Software Foundation.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; see the file LICENSE.  If not, write to the
 * Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA 02111-1307, USA.
 */


package com.mchange.v2.c3p0.codegen;

import java.io.*;
import java.lang.reflect.*;
import java.sql.*;
import com.mchange.v2.codegen.*;
import com.mchange.v2.codegen.intfc.*;
import com.mchange.v2.c3p0.C3P0ProxyConnection;

public class JdbcProxyGenerator extends DelegatorGenerator
{
    JdbcProxyGenerator()
    {
	this.setGenerateInnerSetter( false );
	this.setGenerateInnerGetter( false );
	this.setGenerateNoArgConstructor( false );
	this.setGenerateWrappingConstructor( true );
	this.setClassModifiers( Modifier.PUBLIC | Modifier.FINAL );
	this.setMethodModifiers( Modifier.PUBLIC | Modifier.FINAL );
    }

    static final class NewProxyMetaDataGenerator extends JdbcProxyGenerator
    { 
	protected void generateDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException 
	{
	    String mname   = method.getName();
	    Class  retType = method.getReturnType();
	    
	    if ( ResultSet.class.isAssignableFrom( retType ) )
		{
		    iw.println("ResultSet innerResultSet = inner." + CodegenUtils.methodCall( method ) + ";");
		    iw.println("return new NewProxyResultSet( innerResultSet, parentPooledConnection, inner, this );"); 
		}
	    else
		super.generateDelegateCode( intfcl, genclass, method, iw );
	}

	protected void generatePreDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException 
	{
	    if ( method.getExceptionTypes().length > 0 )
		super.generatePreDelegateCode( intfcl, genclass, method, iw );
	}
	
	protected void generatePostDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException 
	{
	    if ( method.getExceptionTypes().length > 0 )
		super.generatePostDelegateCode( intfcl, genclass, method, iw );
	}
    }

    static final class NewProxyResultSetGenerator extends JdbcProxyGenerator
    {
	protected void generateDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException 
	{
	    String mname   = method.getName();
	    Class  retType = method.getReturnType();

	    if ( mname.equals("close") )
		{
		    iw.println("if (creator instanceof Statement)");
		    iw.upIndent();
 		    iw.println("parentPooledConnection.markInactiveResultSetForStatement( (Statement) creator, inner );");
		    iw.downIndent();
		    iw.println("else if (creator instanceof DatabaseMetaData)");
		    iw.upIndent();
 		    iw.println("parentPooledConnection.markInactiveMetaDataResultSet( inner );");
		    iw.downIndent();
		    iw.println("else if (creator instanceof Connection)");
		    iw.upIndent();
 		    iw.println("parentPooledConnection.markInactiveRawConnectionResultSet( inner );");
		    iw.downIndent();
		    iw.println("else throw new InternalError(\042Must be Statement or DatabaseMetaData -- Bad Creator: \042 + creator);");
 
		    iw.println("this.detach();");
		    iw.println("inner.close();");
		}
	    else if ( mname.equals("getStatement") )
		{
		    iw.println("if (creator instanceof Statement)");
		    iw.upIndent();
 		    iw.println("return (Statement) creatorProxy;");
		    iw.downIndent();
		    iw.println("else if (creator instanceof DatabaseMetaData)");
		    iw.upIndent();
 		    iw.println("return null;");
		    iw.downIndent();
		    iw.println("else throw new InternalError(\042Must be Statement or DatabaseMetaData -- Bad Creator: \042 + creator);");
		}
	    else if ( mname.equals("isClosed") )
		{
		    iw.println( "return parentPooledConnection != null;" );
		}
	    else
		super.generateDelegateCode( intfcl, genclass, method, iw );
	}

	protected void generateExtraDeclarations( Class intfcl, String genclass, IndentedWriter iw ) throws IOException
	{
	    super.generateExtraDeclarations( intfcl, genclass, iw );
	    iw.println();
	    iw.println("Object creator;");
	    iw.println("Object creatorProxy;");
	    iw.println();
	    iw.print( CodegenUtils.fqcnLastElement( genclass ) );
	    iw.println("( " + CodegenUtils.simpleClassName( intfcl ) + " inner, NewPooledConnection parentPooledConnection, Object c, Object cProxy )");
	    iw.println("{");
	    iw.upIndent();
	    iw.println("this( inner, parentPooledConnection );");
	    iw.println("this.creator      = c;");
	    iw.println("this.creatorProxy = cProxy;");
	    iw.downIndent();
	    iw.println("}");
	}
    }

    static final class NewProxyAnyStatementGenerator extends JdbcProxyGenerator
    {
	protected void generateDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException 
	{
	    String mname   = method.getName();
	    Class  retType = method.getReturnType();

	    if ( ResultSet.class.isAssignableFrom( retType ) )
		{
		    iw.println("ResultSet innerResultSet = inner." + CodegenUtils.methodCall( method ) + ";");
		    iw.println("parentPooledConnection.markActiveResultSetForStatement( inner, innerResultSet );");
		    iw.println("return new NewProxyResultSet( innerResultSet, parentPooledConnection, inner, this );"); 
		}
	    else if ( mname.equals("getConnection") )
		{
		    iw.println("return creatorProxy;");
		}
	    else if ( mname.equals("close") )
		{
		    iw.println("if ( is_cached )");
		    iw.upIndent();
		    iw.println("parentPooledConnection.checkinStatement( inner );");
		    iw.downIndent();
		    iw.println("else");
		    iw.println("{");
		    iw.upIndent();

		    iw.println("parentPooledConnection.markInactiveUncachedStatement( inner );");
		    iw.println("this.detach();");
		    iw.println("inner.close();");
		    iw.downIndent();
		    iw.println("}");
		}
	    else if ( mname.equals("isClosed") )
		{
		    iw.println( "return parentPooledConnection != null;" );
		}
	    else
		super.generateDelegateCode( intfcl, genclass, method, iw );
	}

	protected void generateExtraDeclarations( Class intfcl, String genclass, IndentedWriter iw ) throws IOException
	{
	    super.generateExtraDeclarations( intfcl, genclass, iw );
	    iw.println();
	    iw.println("boolean is_cached;");
	    iw.println("NewProxyConnection creatorProxy;");
	    iw.println();
	    iw.print( CodegenUtils.fqcnLastElement( genclass ) );
	    iw.println("( " + CodegenUtils.simpleClassName( intfcl ) + 
		       " inner, NewPooledConnection parentPooledConnection, boolean cached, NewProxyConnection cProxy )");
	    iw.println("{");
	    iw.upIndent();
	    iw.println("this( inner, parentPooledConnection );");
	    iw.println("this.is_cached = cached;");
	    iw.println("this.creatorProxy = cProxy;");
	    iw.downIndent();
	    iw.println("}");
	}
    }

// 	protected void generateExtraDeclarations( Class intfcl, String genclass, IndentedWriter iw ) throws IOException
// 	{
// 	    super.generateExtraDeclarations( intfcl, genclass, iw );
// 	    iw.println();
// 	    iw.println("Statement creatingStatement;");
// 	    iw.println();
// 	    iw.print( CodegenUtils.fqcnLastElement( genclass ) );
// 	    iw.println("( " + CodegenUtils.simpleClassName( intfcl.getClass() ) + " inner, NewPooledConnection parentPooledConnection, Statement stmt )");
// 	    iw.println("{");
// 	    iw.upIndent();
// 	    iw.println("this( inner, parentPooledConnection );");
// 	    iw.println("this.creatingStatement = stmt;");
// 	    iw.downIndent();
// 	    iw.println("}");
// 	}

    static final class NewProxyConnectionGenerator extends JdbcProxyGenerator
    {
	{
	    this.setMethodModifiers( Modifier.PUBLIC | Modifier.SYNCHRONIZED );
	    this.setExtraInterfaces( new Class[] { C3P0ProxyConnection.class } );
	}

	protected void generateDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException 
	{
	    String mname = method.getName();
	    if (mname.equals("createStatement"))
		{
		    iw.println("Statement innerStmt = inner."  + CodegenUtils.methodCall( method ) + ";");
		    iw.println("parentPooledConnection.markActiveUncachedStatement( innerStmt );");
		    iw.println("return new NewProxyStatement( innerStmt, parentPooledConnection, false, this );");
		}
	    else if (mname.equals("prepareStatement"))
		{
		    iw.println("PreparedStatement innerStmt;");
		    iw.println();
		    iw.println("if ( parentPooledConnection.isStatementCaching() )");
		    iw.println("{");
		    iw.upIndent();
		    
		    generateFindMethodAndArgs( method, iw );
		    iw.println("innerStmt = (PreparedStatement) parentPooledConnection.checkoutStatement( method, args );");
		    iw.println("return new NewProxyPreparedStatement( innerStmt, parentPooledConnection, true, this );");

		    iw.downIndent();
		    iw.println("}");
		    iw.println("else");
		    iw.println("{");
		    iw.upIndent();

		    iw.println("innerStmt = inner."  + CodegenUtils.methodCall( method ) + ";");
		    iw.println("parentPooledConnection.markActiveUncachedStatement( innerStmt );");
		    iw.println("return new NewProxyPreparedStatement( innerStmt, parentPooledConnection, false, this );");

		    iw.downIndent();
		    iw.println("}");

		}
	    else if (mname.equals("prepareCall"))
		{
		    iw.println("CallableStatement innerStmt;");
		    iw.println();
		    iw.println("if ( parentPooledConnection.isStatementCaching() )");
		    iw.println("{");
		    iw.upIndent();
		    
		    generateFindMethodAndArgs( method, iw );
		    iw.println("innerStmt = (CallableStatement) parentPooledConnection.checkoutStatement( method, args );");
		    iw.println("return new NewProxyCallableStatement( innerStmt, parentPooledConnection, true, this );");

		    iw.downIndent();
		    iw.println("}");
		    iw.println("else");
		    iw.println("{");
		    iw.upIndent();

		    iw.println("innerStmt = inner." + CodegenUtils.methodCall( method ) + ";");
		    iw.println("parentPooledConnection.markActiveUncachedStatement( innerStmt );");
		    iw.println("return new NewProxyCallableStatement( innerStmt, parentPooledConnection, false, this );");

		    iw.downIndent();
		    iw.println("}");

		}
	    else if (mname.equals("getMetaData"))
		{
		    iw.println("if (this.metaData == null)");
		    iw.println("{");
		    iw.upIndent();
		    iw.println("DatabaseMetaData innerMetaData = inner." + CodegenUtils.methodCall( method ) + ";");
		    iw.println("this.metaData = new NewProxyDatabaseMetaData( innerMetaData, parentPooledConnection );");
		    iw.downIndent();
		    iw.println("}");
		    iw.println("return this.metaData;");
		}
	    else if ( mname.equals("close") )
		{
		    iw.println("NewPooledConnection npc = parentPooledConnection;");
		    iw.println("this.detach();");
		    iw.println("npc.markClosedProxyConnection( this );");
		}
	    else if ( mname.equals("isClosed") )
		{
		    iw.println("return (this.parentPooledConnection == null);");
		}
	    else
		super.generateDelegateCode( intfcl, genclass, method, iw );
	}

	protected void generateExtraDeclarations( Class intfcl, String genclass, IndentedWriter iw ) throws IOException
	{
	    iw.println("DatabaseMetaData metaData = null;");
	    iw.println();
	    iw.println("public Object rawConnectionOperation(Method m, Object target, Object[] args)");
	    iw.upIndent();
	    iw.println("throws IllegalAccessException, IllegalArgumentException, InvocationTargetException");
	    iw.downIndent();
	    iw.println("{");
	    iw.upIndent();
	    iw.println("if ( target == C3P0ProxyConnection.RAW_CONNECTION)");
	    iw.upIndent();
	    iw.println("target = inner;");
	    iw.downIndent();
		
	    iw.println("for (int i = 0, len = args.length; i < len; ++i)");
	    iw.upIndent();
	    iw.println("if (args[i] == C3P0ProxyConnection.RAW_CONNECTION)");
	    iw.upIndent();
	    iw.println("args[i] = inner;");
	    iw.downIndent();
	    iw.downIndent();
		
	    iw.println("Object out = m.invoke( target, args );");
	    iw.println();
	    iw.println("// we never cache Statements generated by an operation on the raw Connection");
	    iw.println("if (out instanceof CallableStatement)");
	    iw.println("{");
	    iw.upIndent();
	    iw.println("CallableStatement innerStmt = (CallableStatement) out;");
	    iw.println("parentPooledConnection.markActiveUncachedStatement( innerStmt );");
	    iw.println("out = new NewProxyCallableStatement( innerStmt, parentPooledConnection, false, this );");
	    iw.downIndent();
	    iw.println("}");
	    iw.println("else if (out instanceof PreparedStatement)");
	    iw.println("{");
	    iw.upIndent();
	    iw.println("PreparedStatement innerStmt = (PreparedStatement) out;");
	    iw.println("parentPooledConnection.markActiveUncachedStatement( innerStmt );");
	    iw.println("out = new NewProxyPreparedStatement( innerStmt, parentPooledConnection, false, this );");
	    iw.downIndent();
	    iw.println("}");
	    iw.println("else if (out instanceof Statement)");
	    iw.println("{");
	    iw.upIndent();
	    iw.println("Statement innerStmt = (Statement) out;");
	    iw.println("parentPooledConnection.markActiveUncachedStatement( innerStmt );");
	    iw.println("out = new NewProxyStatement( innerStmt, parentPooledConnection, false, this );");
	    iw.downIndent();
	    iw.println("}");
	    iw.println("else if (out instanceof ResultSet)");
	    iw.println("{");
	    iw.upIndent();
	    iw.println("ResultSet innerRs = (ResultSet) out;");
	    iw.println("parentPooledConnection.markActiveRawConnectionResultSet( innerRs );");
	    iw.println("out = new NewProxyResultSet( innerRs, parentPooledConnection, inner, this );");	
	    iw.downIndent();
	    iw.println("}");
	    iw.println("else if (out instanceof DatabaseMetaData)");
	    iw.upIndent();
	    iw.println("out = new NewProxyDatabaseMetaData( (DatabaseMetaData) out, parentPooledConnection );");
	    iw.downIndent();
	    iw.println("return out;");
	    iw.downIndent();
	    iw.println("}");

	    super.generateExtraDeclarations( intfcl, genclass, iw );
	}

	void generateFindMethodAndArgs( Method method, IndentedWriter iw ) throws IOException
	{
	    iw.println("Class[] argTypes = ");
	    iw.println("{");
	    iw.upIndent();
	    
	    Class[] argTypes = method.getParameterTypes();
	    for (int i = 0, len = argTypes.length; i < len; ++i)
		{
		    if (i != 0) iw.println(",");
		    iw.print( CodegenUtils.simpleClassName( argTypes[i] ) + ".class" );
		}
	    iw.println();
	    iw.downIndent();
	    iw.println("};");
	    iw.println("Method method = Connection.class.getMethod( \042" + method.getName() + "\042 , argTypes );");
	    iw.println();
	    iw.println("Object[] args = ");
	    iw.println("{");
	    iw.upIndent();
	    
	    for (int i = 0, len = argTypes.length; i < len; ++i)
		{
		    if (i != 0) iw.println(",");
		    String argName = CodegenUtils.generatedArgumentName( i );
		    Class argType = argTypes[i];
		    if (argType.isPrimitive())
			{
			    if (argType == boolean.class)
				iw.print( "Boolean.valueOf( " + argName + " )" );
			    else if (argType == byte.class)
				iw.print( "new Byte( " + argName + " )" );
			    else if (argType == char.class)
				iw.print( "new Character( " + argName + " )" );
			    else if (argType == short.class)
				iw.print( "new Short( " + argName + " )" );
			    else if (argType == int.class)
				iw.print( "new Integer( " + argName + " )" );
			    else if (argType == long.class)
				iw.print( "new Long( " + argName + " )" );
			    else if (argType == float.class)
				iw.print( "new Float( " + argName + " )" );
			    else if (argType == double.class)
				iw.print( "new Double( " + argName + " )" );
			}
		    else
			iw.print( argName );
		}
	    
	    iw.downIndent();
	    iw.println("};");
	}

	protected void generateExtraImports( IndentedWriter iw ) throws IOException
	{
	    super.generateExtraImports( iw );
	    iw.println("import java.lang.reflect.InvocationTargetException;");
	}
    }

    protected void generatePreDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException 
    {
	generateTryOpener( iw );
    }
    
    protected void generatePostDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException 
    {
	generateTryCloserAndCatch( iw );
    }

    static void generateTryOpener( IndentedWriter iw ) throws IOException
    {
	iw.println("try");
	iw.println("{");
	iw.upIndent();
    }

    static void generateTryCloserAndCatch( IndentedWriter iw ) throws IOException
    {
	iw.downIndent();
	iw.println("}");
	iw.println("catch (NullPointerException exc)");
	iw.println("{");
	iw.upIndent();
	iw.println("if ( this.isDetached() )");
	iw.println("{");
	iw.upIndent();
	iw.println( "System.err.print(\042probably 'cuz we're closed -- \042);" );
	iw.println( "exc.printStackTrace();" );
	iw.println( "throw new SQLException(\042You can't operate on a closed connection!!!\042);");
	iw.downIndent();
	iw.println("}");
	iw.println( "else throw exc;" );
	iw.downIndent();
	iw.println("}");
	iw.println("catch (Exception exc)");
	iw.println("{");
	iw.upIndent();
	iw.println("if (! this.isDetached())");
	iw.println("{");
	iw.upIndent();
	iw.println( "exc.printStackTrace();" );
	iw.println( "throw parentPooledConnection.handleThrowable( exc );" );
	iw.downIndent();
	iw.println("}");
	iw.println("else throw SqlUtils.toSQLException( exc );");
	iw.downIndent();
	iw.println("}");
    }

    protected void generateExtraDeclarations( Class intfcl, String genclass, IndentedWriter iw ) throws IOException
    {
	iw.println("NewPooledConnection parentPooledConnection;");
	iw.println();

	iw.println("ConnectionEventListener cel = new ConnectionEventListener()");
	iw.println("{");
	iw.upIndent();

	iw.println("public void connectionErrorOccurred(ConnectionEvent evt)");
	iw.println("{ detach(); }");
	iw.println();
	iw.println("public void connectionClosed(ConnectionEvent evt)");
	iw.println("{ detach(); }");

	iw.downIndent();
	iw.println("};");
	iw.println();
	
	iw.println("void attach( NewPooledConnection parentPooledConnection )");
	iw.println("{");
	iw.upIndent();
	iw.println("this.parentPooledConnection = parentPooledConnection;");
	iw.println("parentPooledConnection.addConnectionEventListener( cel );");
	iw.downIndent();
	iw.println("}");
	iw.println();
	iw.println("private void detach()");
	iw.println("{");
	iw.upIndent();
	iw.println("parentPooledConnection.removeConnectionEventListener( cel );");
	iw.println("parentPooledConnection = null;");
	iw.downIndent();
	iw.println("}");
	iw.println();
	iw.print( CodegenUtils.fqcnLastElement( genclass ) );
	iw.println("( " + CodegenUtils.simpleClassName( intfcl ) + " inner, NewPooledConnection parentPooledConnection )");
	iw.println("{");
	iw.upIndent();
	iw.println("this( inner );");
	iw.println("attach( parentPooledConnection );");
	generateExtraConstructorCode( intfcl, genclass,  iw );
	iw.downIndent();
	iw.println("}");
	iw.println();
	iw.println("boolean isDetached()");
	iw.println("{ return (this.parentPooledConnection == null); }");
    }

    protected void generateExtraImports( IndentedWriter iw ) throws IOException
    {
	iw.println("import java.sql.*;");
	iw.println("import javax.sql.*;");
	iw.println("import java.lang.reflect.Method;");
	iw.println("import com.mchange.v2.sql.SqlUtils;");
    }

    void generateExtraConstructorCode( Class intfcl, String genclass, IndentedWriter iw ) throws IOException
    {}

    public static void main( String[] argv )
    {
	try
	    {
		if (argv.length != 1)
		    {
			System.err.println("java " + JdbcProxyGenerator.class.getName() + " <source-root-directory>");
			return;
		    }

		File srcroot = new File( argv[0] );
		if (! srcroot.exists() || !srcroot.canWrite() )
		    {
			System.err.println(JdbcProxyGenerator.class.getName() + " -- sourceroot: " + argv[0] + " must exist and be writable");
			return;
		    }

		DelegatorGenerator mdgen = new NewProxyMetaDataGenerator();
		DelegatorGenerator rsgen = new NewProxyResultSetGenerator();
		DelegatorGenerator stgen = new NewProxyAnyStatementGenerator();
		DelegatorGenerator cngen = new NewProxyConnectionGenerator();
		
		genclass( cngen, Connection.class, "com.mchange.v2.c3p0.impl.NewProxyConnection", srcroot );
		genclass( stgen, Statement.class, "com.mchange.v2.c3p0.impl.NewProxyStatement", srcroot );
		genclass( stgen, PreparedStatement.class, "com.mchange.v2.c3p0.impl.NewProxyPreparedStatement", srcroot );
		genclass( stgen, CallableStatement.class, "com.mchange.v2.c3p0.impl.NewProxyCallableStatement", srcroot );
		genclass( rsgen, ResultSet.class, "com.mchange.v2.c3p0.impl.NewProxyResultSet", srcroot );
		genclass( mdgen, DatabaseMetaData.class, "com.mchange.v2.c3p0.impl.NewProxyDatabaseMetaData", srcroot );
	    }
	catch ( Exception e )
	    { e.printStackTrace(); }
    }

    static void genclass( DelegatorGenerator dg, Class intfcl, String fqcn, File srcroot ) throws IOException
    {
	File genDir = new File( srcroot, dirForFqcn( fqcn ) );
	if (! genDir.exists() )
	    {
		System.err.println( JdbcProxyGenerator.class.getName() + " -- creating directory: " + genDir.getAbsolutePath() );
		genDir.mkdirs();
	    }
	String fileName = CodegenUtils.fqcnLastElement( fqcn ) + ".java";
	Writer w = null;
	try
	    {
		w = new BufferedWriter( new FileWriter( new File( genDir, fileName ) ) );
		dg.writeDelegator( intfcl, fqcn, w );
		w.flush();
		System.err.println("Generated " + fileName);
	    }
	finally
	    {
		try { if (w != null) w.close(); }
		catch ( Exception e )
		    { e.printStackTrace(); }
	    }		
    }

    static String dirForFqcn( String fqcn )
    {
	int last_dot = fqcn.lastIndexOf('.');
	StringBuffer sb = new StringBuffer( fqcn.substring( 0, last_dot + 1) );
	for (int i = 0, len = sb.length(); i < len; ++i)
	    if (sb.charAt(i) == '.')
		sb.setCharAt(i, '/');
	return sb.toString();
    }
}