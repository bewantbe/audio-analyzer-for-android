% GNU Octave script
warning ("off", "Octave:broadcast");

% Physical constants, from CODATA 2014
c  = 299792458;
h  = 6.626070040e-34;  % Planck constant
kB = 1.38064852e-23;   % Boltzmann constant
c1 = 2 * h * c * c;
c2 = h * c / kB;
PlanksLaw = @(T, lambda) c1 ./ lambda .^ 5 ./ (exp(c2 ./ lambda ./ T) - 1);

% Temperature range
T0 = 300;
T1 = 12000;
n_div = 256;

% Calculate CIE XYZ of the blackbody spectrum
cmf = load('ciexyzjv.csv');
%T_s = fliplr(1 ./ linspace(1/T1, 1/T0, n_div));
%T_s = linspace(T0, T1, n_div);
e_T = 4;
T_s = linspace(T0^(1/e_T), T1^(1/e_T), n_div).^e_T;
rgb = zeros(length(T_s), 3);
for id_T = 1:length(T_s)
  rgb(id_T, :) = cmf(:, 2:4)' * PlanksLaw(T_s(id_T), cmf(:, 1)*1e-9);
end
rgb ./= rgb(:, 2);  % more uniform lighting
rgb .*= 1 - atan(1 ./ ((T_s-T0+1)' / 2000) .^ 1.5) / pi * 2;  % light adjust
rgb *= 0.8;         % factor to make RGB brighter

% Convert to sRGB
sRGB_M = [
 3.2406 -1.5372 -0.4986
-0.9689  1.8758  0.0415
 0.0557 -0.2040  1.0570];

CSRGB = @(c) (12.92*c).*(c<=0.0031308) + (1.055*c.^(1/2.4)-0.055) .* (1-(c<=0.0031308));

rgb = rgb * sRGB_M';  % CIE XYZ to sRGB RGB (linear)
rgb = CSRGB(rgb);

rgb(rgb>1) = 1;
rgb(rgb<0) = 0;

figure(1);
subplot(2,1,1);
rgbplot(rgb, 'profile');
xlim([0 length(rgb)]);
subplot(2,1,2);
rgbplot(rgb, 'composite');

% inv gamma
invCSRGB = @(cs) (cs/12.92).*(cs<=0.040449936) + (((cs+0.055)/1.055).^2.4) .* (1 - (cs<=0.040449936));
figure(2);
plot(1:length(T_s), invCSRGB(rgb) * [0.2126 0.7152 0.0722]');
title('Relative luminance');

c = rgb;
c(2,:) = [5/255 0 0];  % hand tune, make it brighter

fid = fopen('./blackbody.csv', 'w');
for i=2:length(c)  % ignore the first value, which should be filled by black
  fprintf(fid, '%.16f, %.16f, %.16f\n', c(i,1), c(i,2), c(i,3));
end
fclose(fid);
# then the file balckbody.csv can be read by cm_list.py

%v=int32(floor(flipud(c)*255.99)*[0x10000 0x100 1]');
%s=reshape(sprintf('0x%06x, ', v), 10*8, [])(1:end-1,:)';  s(end)=' '

cm_ex1 = hot(256);

figure(9);
subplot(2,1,1);
rgbplot(cm_ex1, 'profile');
xlim([0 length(cm_ex1)]);
subplot(2,1,2);
rgbplot(cm_ex1, 'composite');
title('hot');

figure(10);
plot(1:length(cm_ex1), invCSRGB(cm_ex1) * [0.2126 0.7152 0.0722]');
title('Relative luminance (hot)');

%{
% approximation
function [x y] = CIE_xy_Temp(T)
  % in CIE xy
  if (T < 4000)
      x = -0.2661239*1e9/T/T/T - 0.2343580*1e6/T/T + 0.8776956*1e3/T + 0.179910;
  else
      x = -3.0258469*1e9/T/T/T + 2.1070379*1e6/T/T + 0.2226347*1e3/T + 0.240390;
  end
  if (T < 2222)
      y = -1.1063814*x*x*x - 1.34811020*x*x + 2.18555832*x - 0.20219683;
  elseif (T < 4000)
      y = -0.9549476*x*x*x - 1.37418593*x*x + 2.09137015*x - 0.16748867;
  else
      y =  3.0817580*x*x*x - 5.87338670*x*x + 3.75112997*x - 0.37001483;
  end
endfunction

function [r,g,b] = rgb_temp(T, Y)
  [x y] = CIE_xy_Temp(T);
  X = Y * x / y;
  Z = Y * (1-x-y) / y;
  CSRGB = @(c) (12.92*c).*(c<=0.0031308) + (1.055*c^(1/2.4)-0.055) .* (1-(c<=0.0031308));
  r = CSRGB( 3.2406*X - 1.5372*Y - 0.4986*Z);
  g = CSRGB(-0.9689*X + 1.8758*Y + 0.0415*Z);
  b = CSRGB( 0.0557*X - 0.2040*Y + 1.0570*Z);
endfunction
%}
