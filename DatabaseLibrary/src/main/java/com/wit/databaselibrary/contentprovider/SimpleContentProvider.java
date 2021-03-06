package com.wit.databaselibrary.contentprovider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;

import com.wit.databaselibrary.contentprovider.contract.Contract;

import java.util.List;
import java.util.Map;

public abstract class SimpleContentProvider extends ContentProvider {
	private final List<Contract> contracts;

	public SimpleContentProvider( final List<Contract> contracts ) {
		this.contracts = contracts;
	}

	private String adjustSelection( final Uri uri, String selection, final String authority ) {
		for ( final Contract contract : this.contracts ) {
			if ( contract.uriMatchesObjectId( uri, authority ) ) {
				selection = contract.addSelectionById( uri, selection );

				break;
			}
		}

		return selection;
	}

	@Override
	public int delete( final Uri uri, final String selection,
			final String[] selectionArgs ) {
		final String authority = this.getAuthority();
		final String tableName = this.getTableName( uri, authority );
		final String newSelection = this.adjustSelection( uri, selection, authority );
		final SQLiteOpenHelper databaseHelper = this.getDatabaseHelper();
		final SQLiteDatabase sqLiteDatabase =
				databaseHelper.getWritableDatabase();
		final int count =
				sqLiteDatabase.delete( tableName, newSelection, selectionArgs );

		this.getContext().getContentResolver().notifyChange( uri, null );

		return count;
	}

	protected abstract String getAuthority();

	protected abstract SQLiteOpenHelper getDatabaseHelper();

	protected abstract String getDatabaseName();

	protected abstract int getDatabaseVersion();

	private String getTableName( final Uri uri, final String authority ) {
		String tableName = null;

		for ( final Contract contract : this.contracts ) {
			if ( contract.uriMatches( uri, authority ) ) {
				tableName = contract.getTableName();

				break;
			}
		}

		if ( tableName == null ) {
			throw new IllegalArgumentException( "Unknown URI: " + uri );
		}

		return tableName;
	}

	@Override
	public String getType( final Uri uri ) {
		final String authority = this.getAuthority();
		String contentType = null;

		for ( final Contract contract : this.contracts ) {
			if ( contract.uriMatchesObject( uri, authority ) ) {
				contentType = contract.getContentType();

				break;
			} else if ( contract.uriMatchesObjectId( uri, authority ) ) {
				contentType = contract.getContentItemType();

				break;
			}
		}

		if ( contentType == null ) {
			throw new IllegalArgumentException( "Unknown URI: " + uri );
		}

		return contentType;
	}

	@Override
	public Uri insert( final Uri uri, ContentValues contentValues ) {
		final String authority = this.getAuthority();
		Contract contract = null;

		for ( final Contract currentContract : this.contracts ) {
			if ( currentContract.uriMatchesObject( uri, authority ) ) {
				contract = currentContract;
			}
		}

		if ( contract == null ) {
			throw new IllegalArgumentException( "Unknown URI: " + uri );
		}

		final SQLiteOpenHelper databaseHelper = this.getDatabaseHelper();
		final SQLiteDatabase sqLiteDatabase =
				databaseHelper.getWritableDatabase();

		if ( contentValues == null ) {
			contentValues = new ContentValues();
		}

		final String tableName = contract.getTableName();
		final Uri contentUri = contract.getContentUri( authority );
		final String nullColumnHack;

		if ( contentValues.size() == 0 ) {
			nullColumnHack = BaseColumns._ID;
		} else {
			nullColumnHack = null;
		}

		final long rowId =
				sqLiteDatabase.insert( tableName, nullColumnHack, contentValues );

		if ( rowId > 0 ) {
			final Uri contentUriWithAppendedId =
					ContentUris.withAppendedId( contentUri, rowId );

			this.getContext().getContentResolver().notifyChange(
					contentUriWithAppendedId, null );

			return contentUriWithAppendedId;
		} else {
			throw new SQLException( "Failed to insert row into " + uri );
		}
	}

	@Override
	public Cursor query( final Uri uri, final String[] projection,
			final String selection, final String[] selectionArgs,
			final String sortOrder ) {
		final String authority = this.getAuthority();
		Map<String, String> projectionMap = null;

		for ( final Contract contract : this.contracts ) {
			if ( contract.uriMatches( uri, authority ) ) {
				projectionMap = contract.getProjectionMap();
			}
		}

		if ( projectionMap == null ) {
			throw new IllegalArgumentException( "Unknown URI: " + uri );
		}

		String newSelection;

		if ( selection == null ) {
			newSelection = this.adjustSelection( uri, "", authority );
		} else {
			newSelection = this.adjustSelection( uri, selection, authority );
		}

		final SQLiteQueryBuilder sqLiteQueryBuilder = new SQLiteQueryBuilder();
		final String tableName = this.getTableName( uri, authority );

		sqLiteQueryBuilder.setTables( tableName );
		sqLiteQueryBuilder.setProjectionMap( projectionMap );

		final SQLiteOpenHelper databaseHelper = this.getDatabaseHelper();
		final SQLiteDatabase sqLiteDatabase =
				databaseHelper.getReadableDatabase();
		final Cursor cursor =
				sqLiteQueryBuilder.query( sqLiteDatabase, projection,
						newSelection, selectionArgs, null, null, sortOrder );

		cursor.setNotificationUri( this.getContext().getContentResolver(), uri );

		return cursor;
	}

	@Override
	public int update( final Uri uri, final ContentValues contentValues,
			final String selection, final String[] selectionArgs ) {
		final SQLiteOpenHelper databaseHelper = this.getDatabaseHelper();
		final SQLiteDatabase sqLiteDatabase =
				databaseHelper.getWritableDatabase();
		final String authority = this.getAuthority();
		final String tableName = this.getTableName( uri, authority );

		final int count =
				sqLiteDatabase.update( tableName, contentValues, selection,
						selectionArgs );

		this.getContext().getContentResolver().notifyChange( uri, null );

		return count;
	}
}