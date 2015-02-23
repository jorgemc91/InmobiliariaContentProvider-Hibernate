package com.example.jorge.inmobiliariaconhibernate;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;



import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;


public class Principal extends Activity {
    private Adaptador ad;
    private Cursor cursor;

    private final int ACTIVIDADDOS = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.principal);
        Uri uri = Contrato.TablaInmueble.CONTENT_URI;
        String [] proyeccion = null;
        String condicion = null;
        String [] parametros = null;
        String orden = null;

        cursor =  getContentResolver().query (
                uri,
                proyeccion,
                condicion,
                parametros,
                orden);
        final ListView lv = (ListView) findViewById(R.id.listView);
        ad = new Adaptador(this, cursor);
        lv.setAdapter(ad);
        registerForContextMenu(lv);
        ad.notifyDataSetChanged();

        //Para saber en que orientación estamos
        final FragmentoDetalle fdetalle = (FragmentoDetalle)getFragmentManager().findFragmentById(R.id.FDetalle);
        final boolean horizontal = fdetalle != null && fdetalle.isInLayout();

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (horizontal){
                    fdetalle.setInmueble(getApplicationContext(),cursor,i);
                }else {
                    Intent intent = new Intent(Principal.this, Secundaria.class);
                    intent.putExtra("posicion",i);
                    startActivityForResult(intent, ACTIVIDADDOS);
                }
            }
        });
    }
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.principal, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_subir) {
            sincronizar();
        }else if (id == R.id.action_anadir){
            Intent i = new Intent(this,Anadir.class);
            startActivity(i);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int id = item.getItemId();
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        final int index = info.position;
        if (id == R.id.action_borrar) {
            AlertDialog.Builder dialogo = new AlertDialog.Builder(Principal.this);
            dialogo.setTitle("Borrar");
            dialogo.setMessage("¿Desea borrar el inmueble?");
            dialogo.setCancelable(false);
            dialogo.setPositiveButton("Confirmar", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialogo, int id) {
                    cursor.moveToPosition(index);
                    int idI = cursor.getInt(0);
                    getContentResolver().delete(Contrato.TablaInmueble.CONTENT_URI,
                            Contrato.TablaInmueble._ID + " = ?",
                            new String[]{String.valueOf(idI)});
                    tostada("Inmueble eliminado");
                }
            });
            dialogo.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialogo, int id) {
                    dialogo.cancel();
                }
            });
            dialogo.show();
        } else if (id == R.id.action_editar) {
            return editar(index);
        }
        return super.onContextItemSelected(item);
    }

    private boolean editar(final int index) {
        Intent i = new Intent(this,Editar.class);
        i.putExtra("id",index);
        startActivity(i);
        return true;
    }

    private void tostada(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

/************************************* SINCRONIZAR DATOS *****************************************/

    public void sincronizar(){
        new Subir().execute();
    }

    class Subir extends AsyncTask<String,Integer,String> {

        String urlSubir = "http://192.168.1.11:8080/Inmobiliaria/controlsubir";
        ProgressDialog pDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            pDialog = new ProgressDialog(Principal.this);
            pDialog.setMessage("Sincronizando datos");
            pDialog.setCancelable(false);
            pDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            pDialog.show();

        }

        @Override
        protected String doInBackground(String... params) {
            String url = null;
            String r = null;
            for (int i = 0; i < cursor.getCount() ; i++) {
                cursor.moveToPosition(i);
                if (cursor.getString(7).equals("0")){
                    url="http://192.168.1.11:8080/Inmobiliaria/Control?target=inmueble" +
                            "&op=insert" +
                            "&action=op" +
                            "&view=android" +
                            "&direccion="+cursor.getString(1).replace(" ", "%20")+"" +
                            "&numero="+cursor.getString(2).replace(" ", "%20")+"" +
                            "&precio="+cursor.getString(3).replace(" ", "%20")+"" +
                            "&tipo="+cursor.getString(4).replace(" ", "%20")+"" +
                            "&descripcion="+cursor.getString(5).replace(" ", "%20")+"" +
                            "&imagen="+cursor.getString(6)+"";
                    postFile(urlSubir,"archivo",cursor.getString(6));
                    subido(cursor.getString(0));
                    r= subirDatos(url);
                }else{
                    r = getResources().getString(R.string.sincronizados);
                }
            }
            return r;
        }

        @Override
        protected void onPostExecute(String strings) {
            super.onPostExecute(strings);
            Toast.makeText(Principal.this, strings, Toast.LENGTH_SHORT).show();
            pDialog.dismiss();
        }
        /****************** Envia los datos al servidor **********************************/
        public String subirDatos(String data){
            URL url;
            InputStream is = null;
            BufferedReader br;
            try{
                url = new URL(data);
                is = url.openStream();

                br = new BufferedReader(new InputStreamReader(is));
                br.close();
                is.close();
                return getResources().getString(R.string.sinc_finalizada);
            }catch(IOException e){
                System.out.println(e);
            }
            return getResources().getString(R.string.error_sinc);
        }

        /****************** Pone el campo subido a 1 **********************************/
        public void subido(String id){
            Uri uri = Contrato.TablaInmueble.CONTENT_URI;
            ContentValues valores = new ContentValues();
            valores.put(Contrato.TablaInmueble.SUBIDO, "1");
            getContentResolver().update(uri, valores, "_id = ?",new String[]{id + ""});
        }

        /****************** Guarda las imagenes en el servidor **********************************/
        public String postFile(String urlPeticion, String nombreParametro, String archivoAsubir) {
            try {
                URL url = new URL(urlPeticion);
                HttpURLConnection conexion = (HttpURLConnection) url.openConnection();
                conexion.setDoOutput(true);
                conexion.setRequestMethod("POST");
                FileBody fileBody = new FileBody(new File(getExternalFilesDir(null),archivoAsubir));
                MultipartEntity multipartEntity = new MultipartEntity(HttpMultipartMode.STRICT);
                multipartEntity.addPart(nombreParametro, fileBody);
                multipartEntity.addPart("nombre", new StringBody("valor"));
                conexion.setRequestProperty("Content-Type", multipartEntity.getContentType().getValue());
                OutputStream out = conexion.getOutputStream();
                try {
                    multipartEntity.writeTo(out);
                } catch (Exception e){
                    return e.toString();
                }finally {
                    out.close();
                }
                BufferedReader in = new BufferedReader(new InputStreamReader(conexion.getInputStream()));
                in.close();
            } catch (MalformedURLException ex) {
                return null;
            } catch (IOException ex) {
                return null;
            }
            return "Datos sincronizados";
        }
    }
}
